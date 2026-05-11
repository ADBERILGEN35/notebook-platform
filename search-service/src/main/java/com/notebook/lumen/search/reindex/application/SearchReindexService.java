package com.notebook.lumen.search.reindex.application;

import com.notebook.lumen.common.security.worker.WorkerInstanceIds;
import com.notebook.lumen.search.index.application.SearchAuditService;
import com.notebook.lumen.search.index.infrastructure.SearchDocumentRepository;
import com.notebook.lumen.search.index.infrastructure.SearchOrphanCandidateRow;
import com.notebook.lumen.search.provider.SearchProviderRouter;
import com.notebook.lumen.search.reindex.api.SearchReindexJobRequest;
import com.notebook.lumen.search.reindex.api.SearchReindexJobResponse;
import com.notebook.lumen.search.reindex.api.SearchReindexOrphanPreviewItem;
import com.notebook.lumen.search.reindex.api.SearchReindexOrphanPreviewResponse;
import com.notebook.lumen.search.reindex.domain.SearchReindexJob;
import com.notebook.lumen.search.reindex.domain.SearchReindexJobStatus;
import com.notebook.lumen.search.reindex.domain.SearchReindexMode;
import com.notebook.lumen.search.reindex.infrastructure.SearchReindexJobRepository;
import com.notebook.lumen.search.shared.config.SearchProperties;
import com.notebook.lumen.search.shared.exception.SearchException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchReindexService {
  private static final List<SearchReindexJobStatus> ACTIVE_STATUSES =
      List.of(SearchReindexJobStatus.PENDING, SearchReindexJobStatus.RUNNING);

  private final SearchReindexJobRepository repository;
  private final SearchDocumentRepository documentRepository;
  private final SearchProperties properties;
  private final SearchAuditService auditService;
  private final SearchProviderRouter providerRouter;
  private final MeterRegistry meterRegistry;
  private final String workerInstanceId;

  public SearchReindexService(
      SearchReindexJobRepository repository,
      SearchDocumentRepository documentRepository,
      SearchProperties properties,
      SearchAuditService auditService,
      SearchProviderRouter providerRouter,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.documentRepository = documentRepository;
    this.properties = properties;
    this.auditService = auditService;
    this.providerRouter = providerRouter;
    this.meterRegistry = meterRegistry;
    this.workerInstanceId =
        WorkerInstanceIds.resolve(properties.workerInstanceId(), "search-reindex-worker");
  }

  @Transactional
  public SearchReindexJobResponse create(
      SearchReindexJobRequest request, String requestedByService) {
    validate(request);
    failExpiredRunningJobs(Instant.now());
    if (repository.existsByStatusIn(ACTIVE_STATUSES)) {
      throw new SearchException(
          HttpStatus.CONFLICT,
          "REINDEX_JOB_ALREADY_RUNNING",
          "A reindex job is already pending or running");
    }
    Instant now = Instant.now();
    SearchReindexJob job =
        new SearchReindexJob(
            UUID.randomUUID(),
            request.mode(),
            request.workspaceId(),
            request.notebookId(),
            request.cleanupOrphansRequested(),
            request.dryRunCleanupRequested(),
            requestedByService,
            now);
    repository.save(job);
    meterRegistry.counter("search_reindex_jobs_total", "mode", request.mode().name()).increment();
    auditService.record(
        "SEARCH_REINDEX_JOB_CREATED", request.workspaceId(), job.getId(), metadata(job, null));
    return toResponse(job);
  }

  @Transactional
  public Optional<SearchReindexJob> claimNextPending() {
    Instant now = Instant.now();
    failExpiredRunningJobs(now);
    List<SearchReindexJob> jobs =
        repository.findNextForUpdate(SearchReindexJobStatus.PENDING.name());
    if (jobs.isEmpty()) {
      return Optional.empty();
    }
    SearchReindexJob job = jobs.getFirst();
    job.start(
        workerInstanceId, now, now.plusSeconds(properties.reindex().effectiveLockTimeoutSeconds()));
    meterRegistry.counter("search_reindex_claimed_total").increment();
    auditService.record(
        "SEARCH_REINDEX_JOB_STARTED", job.getWorkspaceId(), job.getId(), metadata(job, null));
    return Optional.of(job);
  }

  @Transactional(readOnly = true)
  public SearchReindexJobResponse get(UUID jobId) {
    return toResponse(load(jobId));
  }

  @Transactional
  public SearchReindexJobResponse cancel(UUID jobId) {
    SearchReindexJob job = load(jobId);
    if (!job.terminal()) {
      job.cancel(Instant.now());
      auditService.record(
          "SEARCH_REINDEX_JOB_CANCELLED", job.getWorkspaceId(), job.getId(), metadata(job, null));
    }
    return toResponse(job);
  }

  @Transactional(readOnly = true)
  public SearchReindexOrphanPreviewResponse orphanPreview(UUID jobId, int requestedSize) {
    SearchReindexJob job = load(jobId);
    if (job.getStatus() != SearchReindexJobStatus.COMPLETED) {
      throw new SearchException(
          HttpStatus.CONFLICT,
          "PREVIEW_NOT_READY",
          "Orphan preview is available only after a completed reindex scan");
    }
    int size = Math.max(1, Math.min(requestedSize, 100));
    long orphanCount = countOrphanCandidates(job);
    Instant previewGeneratedAt = Instant.now();
    List<SearchReindexOrphanPreviewItem> items =
        findOrphanCandidates(job, size).stream()
            .map(
                row ->
                    new SearchReindexOrphanPreviewItem(
                        row.getNoteId(),
                        row.getWorkspaceId(),
                        row.getNotebookId(),
                        row.getLastSeenReindexAt(),
                        row.getIndexedAt(),
                        row.getNoteUpdatedAt(),
                        row.getArchivedAt()))
            .toList();
    meterRegistry.counter("search_reindex_orphan_preview_viewed_total").increment();
    meterRegistry.summary("search_reindex_cleanup_preview_count").record(orphanCount);
    auditService.record(
        "SEARCH_REINDEX_ORPHAN_PREVIEW_VIEWED",
        job.getWorkspaceId(),
        job.getId(),
        previewMetadata(job, orphanCount, items.size()));
    return new SearchReindexOrphanPreviewResponse(
        job.getId(),
        job.getMode(),
        job.getWorkspaceId(),
        job.getNotebookId(),
        orphanCount,
        previewGeneratedAt,
        items);
  }

  @Transactional(readOnly = true)
  public boolean cancelled(UUID jobId) {
    return load(jobId).getStatus() == SearchReindexJobStatus.CANCELLED;
  }

  @Transactional
  public SearchReindexJob recordBatch(
      UUID jobId, long scanned, long indexed, long failed, String nextCursor) {
    SearchReindexJob job = load(jobId);
    if (job.getStatus() == SearchReindexJobStatus.CANCELLED) {
      return job;
    }
    Instant now = Instant.now();
    job.recordBatch(scanned, indexed, failed, nextCursor, now);
    job.heartbeat(now, now.plusSeconds(properties.reindex().effectiveLockTimeoutSeconds()));
    meterRegistry.counter("search_reindex_scanned_total").increment(scanned);
    meterRegistry.counter("search_reindex_indexed_total").increment(indexed);
    return job;
  }

  @Transactional
  public void complete(UUID jobId) {
    SearchReindexJob job = load(jobId);
    if (job.getStatus() == SearchReindexJobStatus.CANCELLED) {
      return;
    }
    Instant now = Instant.now();
    job.complete(now);
    meterRegistry.counter("search_reindex_completed_total").increment();
    meterRegistry.gauge("search_reindex_last_run_timestamp", now.getEpochSecond());
    auditService.record(
        "SEARCH_REINDEX_JOB_COMPLETED", job.getWorkspaceId(), job.getId(), metadata(job, null));
  }

  @Transactional
  public SearchReindexJob cleanupAfterSuccessfulScan(UUID jobId) {
    SearchReindexJob job = load(jobId);
    if (job.getStatus() == SearchReindexJobStatus.CANCELLED) {
      return job;
    }
    if (!job.isCleanupOrphansRequested()) {
      job.skipCleanup(Instant.now());
      meterRegistry.counter("search_reindex_cleanup_skipped_total").increment();
      auditService.record(
          "SEARCH_REINDEX_CLEANUP_SKIPPED", job.getWorkspaceId(), job.getId(), metadata(job, null));
      return job;
    }
    if (job.isDryRunCleanup()) {
      return dryRunCleanup(job);
    }
    if (!properties.reindex().orphanCleanupEnabled()) {
      job.skipCleanup(Instant.now());
      meterRegistry.counter("search_reindex_cleanup_skipped_total").increment();
      auditService.record(
          "SEARCH_REINDEX_CLEANUP_SKIPPED", job.getWorkspaceId(), job.getId(), metadata(job, null));
      return job;
    }
    Instant now = Instant.now();
    job.startCleanup(now);
    Timer.Sample sample = Timer.start(meterRegistry);
    auditService.record(
        "SEARCH_REINDEX_CLEANUP_STARTED", job.getWorkspaceId(), job.getId(), metadata(job, null));
    int archived;
    try {
      archived = archiveOrphanCandidates(job, now);
    } finally {
      sample.stop(meterRegistry.timer("search_reindex_cleanup_duration"));
    }
    job.completeCleanup(archived, Instant.now());
    meterRegistry.counter("search_reindex_orphans_archived_total").increment(archived);
    auditService.record(
        "SEARCH_REINDEX_CLEANUP_COMPLETED", job.getWorkspaceId(), job.getId(), metadata(job, null));
    return job;
  }

  private SearchReindexJob dryRunCleanup(SearchReindexJob job) {
    Instant now = Instant.now();
    job.startCleanup(now);
    Timer.Sample sample = Timer.start(meterRegistry);
    auditService.record(
        "SEARCH_REINDEX_CLEANUP_DRY_RUN_STARTED",
        job.getWorkspaceId(),
        job.getId(),
        metadata(job, null));
    long orphanCount;
    try {
      orphanCount = countOrphanCandidates(job);
    } finally {
      sample.stop(meterRegistry.timer("search_reindex_cleanup_duration"));
    }
    Instant completedAt = Instant.now();
    job.completeDryRunCleanup(orphanCount, completedAt);
    meterRegistry.counter("search_reindex_cleanup_dry_run_total").increment();
    meterRegistry.summary("search_reindex_cleanup_preview_count").record(orphanCount);
    auditService.record(
        "SEARCH_REINDEX_CLEANUP_DRY_RUN_COMPLETED",
        job.getWorkspaceId(),
        job.getId(),
        metadata(job, null));
    return job;
  }

  @Transactional
  public void fail(UUID jobId, RuntimeException failure) {
    SearchReindexJob job = load(jobId);
    if (job.getStatus() == SearchReindexJobStatus.CANCELLED) {
      return;
    }
    String error = sanitizedError(failure);
    job.fail(error, Instant.now());
    meterRegistry.counter("search_reindex_failed_total").increment();
    auditService.record(
        "SEARCH_REINDEX_JOB_FAILED", job.getWorkspaceId(), job.getId(), metadata(job, error));
  }

  @Transactional
  public void heartbeat(UUID jobId) {
    SearchReindexJob job = load(jobId);
    if (job.getStatus() == SearchReindexJobStatus.RUNNING) {
      Instant now = Instant.now();
      job.heartbeat(now, now.plusSeconds(properties.reindex().effectiveLockTimeoutSeconds()));
      meterRegistry.gauge("search_reindex_heartbeat_timestamp", now.getEpochSecond());
    }
  }

  private SearchReindexJob load(UUID jobId) {
    return repository
        .findById(jobId)
        .orElseThrow(
            () ->
                new SearchException(
                    HttpStatus.NOT_FOUND, "REINDEX_JOB_NOT_FOUND", "Reindex job not found"));
  }

  private void failExpiredRunningJobs(Instant now) {
    List<SearchReindexJob> expired =
        repository.findExpiredRunningForUpdate(SearchReindexJobStatus.RUNNING.name(), now);
    if (expired == null) {
      expired = List.of();
    }
    for (SearchReindexJob job : expired) {
      String lockedBy = job.getLockedBy();
      job.fail("Reindex worker lock expired", now);
      meterRegistry.counter("search_reindex_stale_failed_total").increment();
      meterRegistry.counter("search_reindex_lock_expired_total").increment();
      auditService.record(
          "SEARCH_REINDEX_JOB_STALE_FAILED",
          job.getWorkspaceId(),
          job.getId(),
          staleMetadata(job, lockedBy));
    }
  }

  private void validate(SearchReindexJobRequest request) {
    if (request == null || request.mode() == null) {
      throw invalid("mode is required");
    }
    if (request.dryRunCleanupRequested() && !request.cleanupOrphansRequested()) {
      throw new SearchException(
          HttpStatus.BAD_REQUEST,
          "INVALID_CLEANUP_MODE",
          "dryRunCleanup requires cleanupOrphans=true");
    }
    if (SearchReindexMode.FULL.equals(request.mode())) {
      return;
    }
    if (SearchReindexMode.WORKSPACE.equals(request.mode()) && request.workspaceId() != null) {
      return;
    }
    if (SearchReindexMode.NOTEBOOK.equals(request.mode())
        && request.workspaceId() != null
        && request.notebookId() != null) {
      return;
    }
    throw invalid("Invalid reindex mode scope");
  }

  private SearchException invalid(String message) {
    return new SearchException(HttpStatus.BAD_REQUEST, "INVALID_REINDEX_REQUEST", message);
  }

  private SearchReindexJobResponse toResponse(SearchReindexJob job) {
    return new SearchReindexJobResponse(
        job.getId(),
        job.getStatus(),
        job.getMode(),
        job.getWorkspaceId(),
        job.getNotebookId(),
        job.getTotalScanned(),
        job.getTotalIndexed(),
        job.getTotalFailed(),
        job.getTotalArchivedOrphans(),
        job.getLastCursor(),
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getFailedAt(),
        job.getCleanupStartedAt(),
        job.getCleanupCompletedAt(),
        job.isCleanupOrphansRequested(),
        job.isCleanupOrphansExecuted(),
        job.isDryRunCleanup(),
        job.getCleanupPreviewCount(),
        job.getCleanupPreviewGeneratedAt(),
        job.getLastError(),
        job.getCreatedAt(),
        job.getUpdatedAt());
  }

  private long countOrphanCandidates(SearchReindexJob job) {
    return documentRepository.countActiveOrphansForReindex(
        job.getId(), job.getWorkspaceId(), job.getNotebookId());
  }

  private List<SearchOrphanCandidateRow> findOrphanCandidates(SearchReindexJob job, int size) {
    return documentRepository.findActiveOrphansForReindex(
        job.getId(), job.getWorkspaceId(), job.getNotebookId(), size);
  }

  private int archiveOrphanCandidates(SearchReindexJob job, Instant now) {
    List<SearchOrphanCandidateRow> candidates =
        documentRepository.findActiveOrphansForReindex(
            job.getId(), job.getWorkspaceId(), job.getNotebookId(), 500);
    int archived =
        documentRepository.archiveActiveOrphansForReindex(
            job.getId(), job.getWorkspaceId(), job.getNotebookId(), now);
    candidates.stream()
        .limit(archived)
        .forEach(candidate -> providerRouter.projectArchive(candidate.getNoteId(), now));
    return archived;
  }

  private Map<String, Object> metadata(SearchReindexJob job, String error) {
    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
    metadata.put("jobId", job.getId().toString());
    metadata.put("mode", job.getMode().name());
    metadata.put("totalScanned", job.getTotalScanned());
    metadata.put("totalIndexed", job.getTotalIndexed());
    metadata.put("totalFailed", job.getTotalFailed());
    metadata.put("cleanupOrphansRequested", job.isCleanupOrphansRequested());
    metadata.put("cleanupOrphansExecuted", job.isCleanupOrphansExecuted());
    metadata.put("dryRunCleanup", job.isDryRunCleanup());
    metadata.put("cleanupPreviewCount", job.getCleanupPreviewCount());
    metadata.put("archivedCount", job.getTotalArchivedOrphans());
    metadata.put("workerInstanceId", workerInstanceId);
    if (job.getNotebookId() != null) {
      metadata.put("notebookId", job.getNotebookId().toString());
    }
    if (job.getStartedAt() != null) {
      metadata.put("durationMs", Duration.between(job.getStartedAt(), Instant.now()).toMillis());
    }
    if (error != null && !error.isBlank()) {
      metadata.put("error", error);
    }
    return Map.copyOf(metadata);
  }

  private Map<String, Object> previewMetadata(
      SearchReindexJob job, long orphanCount, int sampleSize) {
    Map<String, Object> metadata = new java.util.LinkedHashMap<>(metadata(job, null));
    metadata.put("orphanCount", orphanCount);
    metadata.put("sampleSize", sampleSize);
    return Map.copyOf(metadata);
  }

  private Map<String, Object> staleMetadata(SearchReindexJob job, String lockedBy) {
    Map<String, Object> metadata = new java.util.LinkedHashMap<>(metadata(job, null));
    metadata.put("oldStatus", SearchReindexJobStatus.RUNNING.name());
    metadata.put("newStatus", SearchReindexJobStatus.FAILED.name());
    metadata.put("lockedBy", lockedBy == null || lockedBy.isBlank() ? "unknown" : lockedBy);
    return Map.copyOf(metadata);
  }

  private String sanitizedError(RuntimeException failure) {
    String message = failure.getMessage();
    String value =
        message == null || message.isBlank()
            ? failure.getClass().getSimpleName()
            : failure.getClass().getSimpleName() + ": " + message.replaceAll("[\\r\\n\\t]+", " ");
    return value.length() <= 300 ? value : value.substring(0, 300);
  }
}

package com.notebook.lumen.search.reindex.application;

import com.notebook.lumen.search.index.application.SearchAuditService;
import com.notebook.lumen.search.reindex.api.SearchReindexJobRequest;
import com.notebook.lumen.search.reindex.api.SearchReindexJobResponse;
import com.notebook.lumen.search.reindex.domain.SearchReindexJob;
import com.notebook.lumen.search.reindex.domain.SearchReindexJobStatus;
import com.notebook.lumen.search.reindex.domain.SearchReindexMode;
import com.notebook.lumen.search.reindex.infrastructure.SearchReindexJobRepository;
import com.notebook.lumen.search.shared.exception.SearchException;
import io.micrometer.core.instrument.MeterRegistry;
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
  private final SearchAuditService auditService;
  private final MeterRegistry meterRegistry;

  public SearchReindexService(
      SearchReindexJobRepository repository,
      SearchAuditService auditService,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public SearchReindexJobResponse create(
      SearchReindexJobRequest request, String requestedByService) {
    validate(request);
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
    List<SearchReindexJob> jobs =
        repository.findNextForUpdate(SearchReindexJobStatus.PENDING.name());
    if (jobs.isEmpty()) {
      return Optional.empty();
    }
    SearchReindexJob job = jobs.getFirst();
    job.start(Instant.now());
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
    job.recordBatch(scanned, indexed, failed, nextCursor, Instant.now());
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

  private SearchReindexJob load(UUID jobId) {
    return repository
        .findById(jobId)
        .orElseThrow(
            () ->
                new SearchException(
                    HttpStatus.NOT_FOUND, "REINDEX_JOB_NOT_FOUND", "Reindex job not found"));
  }

  private void validate(SearchReindexJobRequest request) {
    if (request == null || request.mode() == null) {
      throw invalid("mode is required");
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
        job.getLastCursor(),
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getFailedAt(),
        job.getLastError(),
        job.getCreatedAt(),
        job.getUpdatedAt());
  }

  private Map<String, Object> metadata(SearchReindexJob job, String error) {
    Map<String, Object> metadata =
        new java.util.LinkedHashMap<>(
            Map.of(
                "jobId",
                job.getId().toString(),
                "mode",
                job.getMode().name(),
                "totalScanned",
                job.getTotalScanned(),
                "totalIndexed",
                job.getTotalIndexed(),
                "totalFailed",
                job.getTotalFailed()));
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

  private String sanitizedError(RuntimeException failure) {
    String message = failure.getMessage();
    String value =
        message == null || message.isBlank()
            ? failure.getClass().getSimpleName()
            : failure.getClass().getSimpleName() + ": " + message.replaceAll("[\\r\\n\\t]+", " ");
    return value.length() <= 300 ? value : value.substring(0, 300);
  }
}

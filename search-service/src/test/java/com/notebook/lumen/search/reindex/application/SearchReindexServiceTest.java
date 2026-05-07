package com.notebook.lumen.search.reindex.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.search.index.application.SearchAuditService;
import com.notebook.lumen.search.index.infrastructure.SearchDocumentRepository;
import com.notebook.lumen.search.index.infrastructure.SearchOrphanCandidateRow;
import com.notebook.lumen.search.provider.SearchProviderRouter;
import com.notebook.lumen.search.reindex.api.SearchReindexJobRequest;
import com.notebook.lumen.search.reindex.api.SearchReindexJobResponse;
import com.notebook.lumen.search.reindex.api.SearchReindexOrphanPreviewResponse;
import com.notebook.lumen.search.reindex.domain.SearchReindexJob;
import com.notebook.lumen.search.reindex.domain.SearchReindexJobStatus;
import com.notebook.lumen.search.reindex.domain.SearchReindexMode;
import com.notebook.lumen.search.reindex.infrastructure.SearchReindexJobRepository;
import com.notebook.lumen.search.shared.config.SearchProperties;
import com.notebook.lumen.search.shared.exception.SearchException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchReindexServiceTest {
  private final SearchReindexJobRepository repository =
      org.mockito.Mockito.mock(SearchReindexJobRepository.class);
  private final SearchDocumentRepository documentRepository =
      org.mockito.Mockito.mock(SearchDocumentRepository.class);
  private final SearchAuditService auditService =
      org.mockito.Mockito.mock(SearchAuditService.class);
  private final SearchProviderRouter providerRouter =
      org.mockito.Mockito.mock(SearchProviderRouter.class);
  private final SearchReindexService service =
      new SearchReindexService(
          repository,
          documentRepository,
          properties(true),
          auditService,
          providerRouter,
          new SimpleMeterRegistry());

  @Test
  void createValidatesModeAndRejectsActiveJob() {
    when(repository.existsByStatusIn(any())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create(
                    new SearchReindexJobRequest(SearchReindexMode.FULL, null, null, false, false),
                    "ops-admin"))
        .isInstanceOf(SearchException.class)
        .extracting("errorCode")
        .isEqualTo("REINDEX_JOB_ALREADY_RUNNING");
  }

  @Test
  void createPersistsPendingJob() {
    when(repository.existsByStatusIn(any())).thenReturn(false);

    SearchReindexJobResponse response =
        service.create(
            new SearchReindexJobRequest(
                SearchReindexMode.WORKSPACE, UUID.randomUUID(), null, true, false),
            "ops-admin");

    assertThat(response.status()).isEqualTo(SearchReindexJobStatus.PENDING);
    ArgumentCaptor<SearchReindexJob> captor = ArgumentCaptor.forClass(SearchReindexJob.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getRequestedByService()).isEqualTo("ops-admin");
  }

  @Test
  void claimStartsPendingJobAndCancelTransitionsToCancelled() {
    SearchReindexJob job = job();
    when(repository.findNextForUpdate(SearchReindexJobStatus.PENDING.name()))
        .thenReturn(List.of(job));
    when(repository.findById(job.getId())).thenReturn(Optional.of(job));

    Optional<SearchReindexJob> claimed = service.claimNextPending();

    assertThat(claimed).contains(job);
    assertThat(job.getStatus()).isEqualTo(SearchReindexJobStatus.RUNNING);

    SearchReindexJobResponse cancelled = service.cancel(job.getId());

    assertThat(cancelled.status()).isEqualTo(SearchReindexJobStatus.CANCELLED);
  }

  @Test
  void expiredRunningJobIsFailedBeforeClaimingNextPendingJob() {
    SearchReindexJob stale = job();
    start(stale);
    SearchReindexJob pending = job();
    when(repository.findExpiredRunningForUpdate(
            org.mockito.ArgumentMatchers.eq(SearchReindexJobStatus.RUNNING.name()), any()))
        .thenReturn(List.of(stale));
    when(repository.findNextForUpdate(SearchReindexJobStatus.PENDING.name()))
        .thenReturn(List.of(pending));

    Optional<SearchReindexJob> claimed = service.claimNextPending();

    assertThat(stale.getStatus()).isEqualTo(SearchReindexJobStatus.FAILED);
    assertThat(stale.getLastError()).contains("lock expired");
    assertThat(claimed).contains(pending);
    assertThat(pending.getStatus()).isEqualTo(SearchReindexJobStatus.RUNNING);
  }

  @Test
  void recordBatchRefreshesHeartbeatAndLockExpiry() {
    SearchReindexJob job = job();
    start(job);
    Instant previousHeartbeat = job.getHeartbeatAt();
    when(repository.findById(job.getId())).thenReturn(Optional.of(job));

    service.recordBatch(job.getId(), 1, 1, 0, null);

    assertThat(job.getHeartbeatAt()).isAfterOrEqualTo(previousHeartbeat);
    assertThat(job.getLockExpiresAt()).isNotNull();
  }

  @Test
  void recordBatchAndFailUpdateProgress() {
    SearchReindexJob job = job();
    start(job);
    when(repository.findById(job.getId())).thenReturn(Optional.of(job));

    service.recordBatch(job.getId(), 10, 9, 1, "cursor");
    service.fail(job.getId(), new IllegalStateException("source unavailable"));

    assertThat(job.getTotalScanned()).isEqualTo(10);
    assertThat(job.getTotalIndexed()).isEqualTo(9);
    assertThat(job.getTotalFailed()).isEqualTo(1);
    assertThat(job.getStatus()).isEqualTo(SearchReindexJobStatus.FAILED);
    assertThat(job.getLastError()).contains("source unavailable");
  }

  @Test
  void cleanupRequiresGlobalAndRequestFlags() {
    SearchReindexJob job = job(false);
    start(job);
    when(repository.findById(job.getId())).thenReturn(Optional.of(job));

    service.cleanupAfterSuccessfulScan(job.getId());

    assertThat(job.isCleanupOrphansExecuted()).isFalse();
    verify(documentRepository, never()).archiveActiveOrphansForReindex(any(), any(), any(), any());
  }

  @Test
  void cleanupArchivesScopedOrphansWhenEnabledAndRequested() {
    SearchReindexJob job = job(true);
    start(job);
    when(repository.findById(job.getId())).thenReturn(Optional.of(job));
    when(documentRepository.archiveActiveOrphansForReindex(
            org.mockito.ArgumentMatchers.eq(job.getId()),
            org.mockito.ArgumentMatchers.eq(job.getWorkspaceId()),
            org.mockito.ArgumentMatchers.eq(job.getNotebookId()),
            any()))
        .thenReturn(4);

    service.cleanupAfterSuccessfulScan(job.getId());

    assertThat(job.isCleanupOrphansExecuted()).isTrue();
    assertThat(job.getTotalArchivedOrphans()).isEqualTo(4);
    assertThat(job.getCleanupStartedAt()).isNotNull();
    assertThat(job.getCleanupCompletedAt()).isNotNull();
  }

  @Test
  void createRejectsDryRunWithoutCleanupOrphans() {
    assertThatThrownBy(
            () ->
                service.create(
                    new SearchReindexJobRequest(
                        SearchReindexMode.WORKSPACE, UUID.randomUUID(), null, false, true),
                    "ops-admin"))
        .isInstanceOf(SearchException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_CLEANUP_MODE");
  }

  @Test
  void dryRunCleanupCountsOrphansWithoutArchiving() {
    SearchReindexJob job = job(true, true);
    start(job);
    when(repository.findById(job.getId())).thenReturn(Optional.of(job));
    when(documentRepository.countActiveOrphansForReindex(
            org.mockito.ArgumentMatchers.eq(job.getId()),
            org.mockito.ArgumentMatchers.eq(job.getWorkspaceId()),
            org.mockito.ArgumentMatchers.eq(job.getNotebookId())))
        .thenReturn(7L);

    service.cleanupAfterSuccessfulScan(job.getId());

    assertThat(job.isCleanupOrphansExecuted()).isFalse();
    assertThat(job.isDryRunCleanup()).isTrue();
    assertThat(job.getCleanupPreviewCount()).isEqualTo(7);
    assertThat(job.getCleanupPreviewGeneratedAt()).isNotNull();
    verify(documentRepository, never()).archiveActiveOrphansForReindex(any(), any(), any(), any());
  }

  @Test
  void dryRunCleanupWorksWhenGlobalCleanupDisabled() {
    SearchReindexService disabledCleanupService =
        new SearchReindexService(
            repository,
            documentRepository,
            properties(false),
            auditService,
            providerRouter,
            new SimpleMeterRegistry());
    SearchReindexJob job = job(true, true);
    start(job);
    when(repository.findById(job.getId())).thenReturn(Optional.of(job));
    when(documentRepository.countActiveOrphansForReindex(any(), any(), any())).thenReturn(3L);

    disabledCleanupService.cleanupAfterSuccessfulScan(job.getId());

    assertThat(job.getCleanupPreviewCount()).isEqualTo(3);
    assertThat(job.isCleanupOrphansExecuted()).isFalse();
    verify(documentRepository, never()).archiveActiveOrphansForReindex(any(), any(), any(), any());
  }

  @Test
  void realCleanupSkipsWhenGlobalCleanupDisabled() {
    SearchReindexService disabledCleanupService =
        new SearchReindexService(
            repository,
            documentRepository,
            properties(false),
            auditService,
            providerRouter,
            new SimpleMeterRegistry());
    SearchReindexJob job = job(true, false);
    start(job);
    when(repository.findById(job.getId())).thenReturn(Optional.of(job));

    disabledCleanupService.cleanupAfterSuccessfulScan(job.getId());

    assertThat(job.isCleanupOrphansExecuted()).isFalse();
    verify(documentRepository, never()).archiveActiveOrphansForReindex(any(), any(), any(), any());
  }

  @Test
  void orphanPreviewRequiresCompletedJob() {
    SearchReindexJob job = job(true, true);
    start(job);
    when(repository.findById(job.getId())).thenReturn(Optional.of(job));

    assertThatThrownBy(() -> service.orphanPreview(job.getId(), 20))
        .isInstanceOf(SearchException.class)
        .extracting("errorCode")
        .isEqualTo("PREVIEW_NOT_READY");
  }

  @Test
  void orphanPreviewReturnsScopedSampleWithoutTitleOrContent() {
    SearchReindexJob job = job(true, true);
    start(job);
    job.completeDryRunCleanup(1, Instant.now());
    job.complete(Instant.now());
    when(repository.findById(job.getId())).thenReturn(Optional.of(job));
    when(documentRepository.countActiveOrphansForReindex(
            org.mockito.ArgumentMatchers.eq(job.getId()),
            org.mockito.ArgumentMatchers.eq(job.getWorkspaceId()),
            org.mockito.ArgumentMatchers.eq(job.getNotebookId())))
        .thenReturn(1L);
    when(documentRepository.findActiveOrphansForReindex(
            org.mockito.ArgumentMatchers.eq(job.getId()),
            org.mockito.ArgumentMatchers.eq(job.getWorkspaceId()),
            org.mockito.ArgumentMatchers.eq(job.getNotebookId()),
            org.mockito.ArgumentMatchers.eq(20)))
        .thenReturn(List.of(row(job.getWorkspaceId(), job.getNotebookId())));

    SearchReindexOrphanPreviewResponse response = service.orphanPreview(job.getId(), 20);

    assertThat(response.orphanCount()).isEqualTo(1);
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().workspaceId()).isEqualTo(job.getWorkspaceId());
    assertThat(response.items().getFirst().notebookId()).isEqualTo(job.getNotebookId());
  }

  private SearchReindexJob job() {
    return job(false);
  }

  private SearchReindexJob job(boolean cleanupOrphansRequested) {
    return job(cleanupOrphansRequested, false);
  }

  private SearchReindexJob job(boolean cleanupOrphansRequested, boolean dryRunCleanup) {
    return new SearchReindexJob(
        UUID.randomUUID(),
        SearchReindexMode.WORKSPACE,
        UUID.randomUUID(),
        null,
        cleanupOrphansRequested,
        dryRunCleanup,
        "ops-admin",
        Instant.now());
  }

  private void start(SearchReindexJob job) {
    Instant now = Instant.now();
    job.start("test-worker", now, now.plusSeconds(300));
  }

  private SearchOrphanCandidateRow row(UUID workspaceId, UUID notebookId) {
    UUID noteId = UUID.randomUUID();
    Instant now = Instant.now();
    return new SearchOrphanCandidateRow() {
      @Override
      public UUID getNoteId() {
        return noteId;
      }

      @Override
      public UUID getWorkspaceId() {
        return workspaceId;
      }

      @Override
      public UUID getNotebookId() {
        return notebookId;
      }

      @Override
      public Instant getIndexedAt() {
        return now;
      }

      @Override
      public Instant getNoteUpdatedAt() {
        return now.minusSeconds(60);
      }

      @Override
      public Instant getArchivedAt() {
        return null;
      }

      @Override
      public Instant getLastSeenReindexAt() {
        return now.minusSeconds(120);
      }
    };
  }

  private SearchProperties properties(boolean orphanCleanupEnabled) {
    return new SearchProperties(
        200000,
        120,
        2,
        50,
        "",
        "postgres",
        false,
        false,
        new SearchProperties.OpenSearch("", "", "", "notebook-notes", 1000, 3000, false, ""),
        new SearchProperties.Workspace("http://localhost", 1000, 2),
        new SearchProperties.ContentSource("http://localhost", 1000, "content-service"),
        null,
        new SearchProperties.Internal(null, null),
        new SearchProperties.Reindex(true, 100, 10, 100, orphanCleanupEnabled, 300, 30));
  }
}

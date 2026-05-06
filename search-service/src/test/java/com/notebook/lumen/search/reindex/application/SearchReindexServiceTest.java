package com.notebook.lumen.search.reindex.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.search.index.application.SearchAuditService;
import com.notebook.lumen.search.reindex.api.SearchReindexJobRequest;
import com.notebook.lumen.search.reindex.api.SearchReindexJobResponse;
import com.notebook.lumen.search.reindex.domain.SearchReindexJob;
import com.notebook.lumen.search.reindex.domain.SearchReindexJobStatus;
import com.notebook.lumen.search.reindex.domain.SearchReindexMode;
import com.notebook.lumen.search.reindex.infrastructure.SearchReindexJobRepository;
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
  private final SearchAuditService auditService =
      org.mockito.Mockito.mock(SearchAuditService.class);
  private final SearchReindexService service =
      new SearchReindexService(repository, auditService, new SimpleMeterRegistry());

  @Test
  void createValidatesModeAndRejectsActiveJob() {
    when(repository.existsByStatusIn(any())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create(
                    new SearchReindexJobRequest(SearchReindexMode.FULL, null, null), "ops-admin"))
        .isInstanceOf(SearchException.class)
        .extracting("errorCode")
        .isEqualTo("REINDEX_JOB_ALREADY_RUNNING");
  }

  @Test
  void createPersistsPendingJob() {
    when(repository.existsByStatusIn(any())).thenReturn(false);

    SearchReindexJobResponse response =
        service.create(
            new SearchReindexJobRequest(SearchReindexMode.WORKSPACE, UUID.randomUUID(), null),
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
  void recordBatchAndFailUpdateProgress() {
    SearchReindexJob job = job();
    job.start(Instant.now());
    when(repository.findById(job.getId())).thenReturn(Optional.of(job));

    service.recordBatch(job.getId(), 10, 9, 1, "cursor");
    service.fail(job.getId(), new IllegalStateException("source unavailable"));

    assertThat(job.getTotalScanned()).isEqualTo(10);
    assertThat(job.getTotalIndexed()).isEqualTo(9);
    assertThat(job.getTotalFailed()).isEqualTo(1);
    assertThat(job.getStatus()).isEqualTo(SearchReindexJobStatus.FAILED);
    assertThat(job.getLastError()).contains("source unavailable");
  }

  private SearchReindexJob job() {
    return new SearchReindexJob(
        UUID.randomUUID(), SearchReindexMode.FULL, null, null, "ops-admin", Instant.now());
  }
}

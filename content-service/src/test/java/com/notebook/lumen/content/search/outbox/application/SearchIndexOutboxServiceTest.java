package com.notebook.lumen.content.search.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxEvent;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxEventType;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxStatus;
import com.notebook.lumen.content.search.outbox.infrastructure.SearchIndexOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class SearchIndexOutboxServiceTest {
  private final SearchIndexOutboxRepository repository =
      org.mockito.Mockito.mock(SearchIndexOutboxRepository.class);
  private final AuditService auditService = org.mockito.Mockito.mock(AuditService.class);
  private final SearchIndexOutboxService service =
      new SearchIndexOutboxService(
          repository, properties(), auditService, new SimpleMeterRegistry());

  @Test
  void claimDueEventsUsesPendingDueQueueAndMarksProcessing() {
    SearchIndexOutboxEvent event = event();
    when(repository.findDueForUpdate(any(), any(), any(Integer.class))).thenReturn(List.of(event));
    when(repository.findStaleProcessingForUpdate(any(), any(), any(Integer.class)))
        .thenReturn(List.of());

    List<SearchIndexOutboxEvent> events = service.claimDueEvents();

    assertThat(events).containsExactly(event);
    assertThat(event.getStatus()).isEqualTo(SearchIndexOutboxStatus.PROCESSING);
    assertThat(event.getLockedAt()).isNotNull();
    assertThat(event.getLockedBy()).isNotBlank();
    assertThat(event.getLockExpiresAt()).isNotNull();
    verify(repository).findDueForUpdate(any(), any(), org.mockito.ArgumentMatchers.eq(25));
  }

  @Test
  void expiredProcessingEventIsRecoveredBeforeClaimingPendingEvents() {
    SearchIndexOutboxEvent stale = event();
    Instant now = Instant.now();
    stale.markProcessing("old-worker", now.minusSeconds(600), now.minusSeconds(300));
    when(repository.findStaleProcessingForUpdate(any(), any(), any(Integer.class)))
        .thenReturn(List.of(stale));
    when(repository.findDueForUpdate(any(), any(), any(Integer.class))).thenReturn(List.of());

    service.claimDueEvents();

    assertThat(stale.getStatus()).isEqualTo(SearchIndexOutboxStatus.PENDING);
    assertThat(stale.getAttemptCount()).isEqualTo(1);
    assertThat(stale.getLockedBy()).isNull();
    assertThat(stale.getLockExpiresAt()).isNull();
  }

  @Test
  void retryUsesExponentialBackoffUntilMaxAttemptsThenFails() {
    SearchIndexOutboxEvent event = event();
    when(repository.findById(event.getId())).thenReturn(Optional.of(event));

    service.markFailedOrRetry(event.getId(), new IllegalStateException("provider down"));

    assertThat(event.getStatus()).isEqualTo(SearchIndexOutboxStatus.PENDING);
    assertThat(event.getAttemptCount()).isEqualTo(1);
    assertThat(event.getNextAttemptAt()).isNotNull();

    service.markFailedOrRetry(event.getId(), new IllegalStateException("provider down"));

    assertThat(event.getStatus()).isEqualTo(SearchIndexOutboxStatus.FAILED);
    assertThat(event.getAttemptCount()).isEqualTo(2);
    assertThat(event.getFailedAt()).isNotNull();
  }

  @Test
  void reprocessFailedResetsFailedEventsToPending() {
    SearchIndexOutboxEvent event = event();
    event.markFailed("failure", Instant.now());
    when(repository.findFailedForReprocess(
            org.mockito.ArgumentMatchers.eq(SearchIndexOutboxStatus.FAILED),
            any(),
            any(),
            any(Pageable.class)))
        .thenReturn(List.of(event));

    int count = service.reprocessFailed(null, null, 10);

    assertThat(count).isEqualTo(1);
    assertThat(event.getStatus()).isEqualTo(SearchIndexOutboxStatus.PENDING);
    assertThat(event.getAttemptCount()).isZero();
    assertThat(event.getLastError()).isNull();
  }

  @Test
  void statusCalculatesCountsAndOldestPendingAge() {
    when(repository.countByStatus(SearchIndexOutboxStatus.PENDING)).thenReturn(2L);
    when(repository.countByStatus(SearchIndexOutboxStatus.PROCESSING)).thenReturn(1L);
    when(repository.countByStatus(SearchIndexOutboxStatus.FAILED)).thenReturn(3L);
    when(repository.findOldestCreatedAtByStatus(SearchIndexOutboxStatus.PENDING))
        .thenReturn(Optional.of(Instant.now().minusSeconds(30)));

    SearchIndexOutboxStatusView status = service.status();

    assertThat(status.pendingCount()).isEqualTo(2);
    assertThat(status.processingCount()).isEqualTo(1);
    assertThat(status.failedCount()).isEqualTo(3);
    assertThat(status.oldestPendingAgeSeconds()).isGreaterThanOrEqualTo(0);
  }

  private SearchIndexOutboxEvent event() {
    return new SearchIndexOutboxEvent(
        UUID.randomUUID(),
        SearchIndexOutboxEventType.NOTE_UPSERT,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        1,
        "{}",
        UUID.randomUUID().toString(),
        Instant.now());
  }

  private ContentProperties properties() {
    return new ContentProperties(
        "",
        null,
        new ContentProperties.Concurrency(false),
        null,
        null,
        new ContentProperties.Search("", 1000, true, null, null, outbox()));
  }

  private ContentProperties.SearchOutbox outbox() {
    return new ContentProperties.SearchOutbox(true, 25, 2, 30, 3600, 10, 300, null);
  }
}

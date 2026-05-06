package com.notebook.lumen.content.search.outbox.application;

import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxEvent;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxStatus;
import com.notebook.lumen.content.search.outbox.infrastructure.SearchIndexOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchIndexOutboxService {
  private final SearchIndexOutboxRepository repository;
  private final ContentProperties properties;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;

  public SearchIndexOutboxService(
      SearchIndexOutboxRepository repository,
      ContentProperties properties,
      AuditService auditService,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.properties = properties;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public List<SearchIndexOutboxEvent> claimDueEvents() {
    Instant now = Instant.now();
    recoverStaleProcessing(now);
    List<SearchIndexOutboxEvent> events =
        repository.findDueForUpdate(
            SearchIndexOutboxStatus.PENDING.name(), now, outbox().effectiveBatchSize());
    events.forEach(event -> event.markProcessing(now));
    return events;
  }

  @Transactional
  public void markProcessed(UUID eventId) {
    SearchIndexOutboxEvent event = load(eventId);
    Instant now = Instant.now();
    event.markProcessed(now);
    meterRegistry.counter("search_outbox_processed_total").increment();
    auditService.record(
        "SEARCH_INDEX_OUTBOX_PROCESSED",
        null,
        event.getWorkspaceId(),
        "NOTE",
        event.getNoteId(),
        auditMetadata(event, null));
  }

  @Transactional
  public void markFailedOrRetry(UUID eventId, RuntimeException failure) {
    SearchIndexOutboxEvent event = load(eventId);
    Instant now = Instant.now();
    String sanitizedError = sanitizedError(failure);
    if (event.getAttemptCount() + 1 >= outbox().effectiveMaxAttempts()) {
      event.markFailed(sanitizedError, now);
      meterRegistry.counter("search_outbox_failed_total").increment();
      auditService.record(
          "SEARCH_INDEX_OUTBOX_FAILED",
          null,
          event.getWorkspaceId(),
          "NOTE",
          event.getNoteId(),
          auditMetadata(event, sanitizedError));
      return;
    }
    Instant nextAttemptAt = now.plus(backoffDelay(event.getAttemptCount()));
    event.markRetry(sanitizedError, nextAttemptAt, now);
    meterRegistry.counter("search_outbox_retry_total").increment();
    auditService.record(
        "SEARCH_INDEX_OUTBOX_RETRY_SCHEDULED",
        null,
        event.getWorkspaceId(),
        "NOTE",
        event.getNoteId(),
        auditMetadata(event, sanitizedError));
  }

  @Transactional
  public int reprocessFailed(UUID workspaceId, UUID noteId, int limit) {
    int effectiveLimit = limit <= 0 ? 100 : Math.min(limit, 1000);
    List<SearchIndexOutboxEvent> events =
        repository.findFailedForReprocess(
            SearchIndexOutboxStatus.FAILED, workspaceId, noteId, PageRequest.of(0, effectiveLimit));
    Instant now = Instant.now();
    events.forEach(event -> event.requeue(now));
    return events.size();
  }

  @Transactional(readOnly = true)
  public SearchIndexOutboxStatusView status() {
    Instant now = Instant.now();
    Long oldestAge =
        repository
            .findOldestCreatedAtByStatus(SearchIndexOutboxStatus.PENDING)
            .map(createdAt -> Duration.between(createdAt, now).getSeconds())
            .orElse(null);
    return new SearchIndexOutboxStatusView(
        repository.countByStatus(SearchIndexOutboxStatus.PENDING),
        repository.countByStatus(SearchIndexOutboxStatus.PROCESSING),
        repository.countByStatus(SearchIndexOutboxStatus.FAILED),
        oldestAge);
  }

  Duration backoffDelay(int previousAttemptCount) {
    long multiplier = 1L << Math.min(previousAttemptCount, 20);
    long delaySeconds = outbox().effectiveInitialDelaySeconds() * multiplier;
    return Duration.ofSeconds(Math.min(delaySeconds, outbox().effectiveMaxDelaySeconds()));
  }

  private SearchIndexOutboxEvent load(UUID eventId) {
    return repository
        .findById(eventId)
        .orElseThrow(
            () -> new IllegalStateException("Search index outbox event not found: " + eventId));
  }

  private void recoverStaleProcessing(Instant now) {
    Instant lockedBefore = now.minusSeconds(outbox().effectiveMaxDelaySeconds());
    repository
        .findStaleProcessingForUpdate(
            SearchIndexOutboxStatus.PROCESSING.name(), lockedBefore, outbox().effectiveBatchSize())
        .forEach(event -> event.markRetry("Processing lock expired", now, now));
  }

  private Map<String, Object> auditMetadata(SearchIndexOutboxEvent event, String error) {
    if (error == null || error.isBlank()) {
      return Map.of(
          "eventType", event.getEventType().name(),
          "attemptCount", event.getAttemptCount());
    }
    return Map.of(
        "eventType",
        event.getEventType().name(),
        "attemptCount",
        event.getAttemptCount(),
        "error",
        error);
  }

  private String sanitizedError(RuntimeException failure) {
    String message = failure.getMessage();
    if (message == null || message.isBlank()) {
      return failure.getClass().getSimpleName();
    }
    String normalized = message.replaceAll("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ****");
    normalized = normalized.replaceAll("[\\r\\n\\t]+", " ");
    if (normalized.length() > 300) {
      normalized = normalized.substring(0, 300);
    }
    return failure.getClass().getSimpleName() + ": " + normalized;
  }

  private ContentProperties.SearchOutbox outbox() {
    ContentProperties.Search search = properties.search();
    if (search == null || search.outbox() == null) {
      return new ContentProperties.SearchOutbox(true, 50, 10, 30, 3600, 10, null);
    }
    return search.outbox();
  }
}

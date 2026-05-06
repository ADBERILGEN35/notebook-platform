package com.notebook.lumen.content.search.outbox.application;

import com.notebook.lumen.content.search.outbox.SearchIndexOutboxStatus;
import com.notebook.lumen.content.search.outbox.infrastructure.SearchIndexOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexOutboxMetrics {
  public SearchIndexOutboxMetrics(
      SearchIndexOutboxRepository repository, MeterRegistry meterRegistry) {
    Gauge.builder(
            "search_outbox_pending",
            repository,
            repo -> repo.countByStatus(SearchIndexOutboxStatus.PENDING))
        .register(meterRegistry);
    Gauge.builder(
            "search_outbox_failed",
            repository,
            repo -> repo.countByStatus(SearchIndexOutboxStatus.FAILED))
        .register(meterRegistry);
    Gauge.builder(
            "search_outbox_oldest_pending_age",
            repository,
            SearchIndexOutboxMetrics::oldestPendingAgeSeconds)
        .register(meterRegistry);
  }

  private static long oldestPendingAgeSeconds(SearchIndexOutboxRepository repository) {
    Instant now = Instant.now();
    return repository
        .findOldestCreatedAtByStatus(SearchIndexOutboxStatus.PENDING)
        .map(createdAt -> Duration.between(createdAt, now).getSeconds())
        .orElse(0L);
  }
}

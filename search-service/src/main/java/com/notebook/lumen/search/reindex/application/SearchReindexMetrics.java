package com.notebook.lumen.search.reindex.application;

import com.notebook.lumen.search.reindex.domain.SearchReindexJobStatus;
import com.notebook.lumen.search.reindex.infrastructure.SearchReindexJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SearchReindexMetrics {
  public SearchReindexMetrics(SearchReindexJobRepository repository, MeterRegistry meterRegistry) {
    Gauge.builder(
            "search_reindex_running",
            repository,
            repo -> repo.countByStatus(SearchReindexJobStatus.RUNNING))
        .register(meterRegistry);
  }
}

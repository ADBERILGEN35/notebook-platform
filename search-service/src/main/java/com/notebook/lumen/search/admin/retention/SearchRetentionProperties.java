package com.notebook.lumen.search.admin.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search.retention")
public record SearchRetentionProperties(
    boolean dryRunCountsEnabled,
    int maxCountQueryLimit,
    int staleDocumentRetentionDays,
    int terminalJobRetentionDays) {

  public SearchRetentionProperties {
    if (maxCountQueryLimit <= 0) {
      maxCountQueryLimit = 100_000;
    }
    if (staleDocumentRetentionDays <= 0) {
      staleDocumentRetentionDays = 90;
    }
    if (terminalJobRetentionDays <= 0) {
      terminalJobRetentionDays = 90;
    }
  }

  public int retentionDaysFor(SearchRetentionTargetKey target) {
    return switch (target) {
      case SEARCH_DOCUMENTS_STALE -> staleDocumentRetentionDays;
      case SEARCH_REINDEX_JOBS_TERMINAL -> terminalJobRetentionDays;
      default -> 0;
    };
  }
}

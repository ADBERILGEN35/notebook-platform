package com.notebook.lumen.search.admin.retention;

import java.util.Optional;

public enum SearchRetentionTargetKey {
  SEARCH_DOCUMENTS_STALE("search.documents_stale", SearchRetentionTargetStatus.DRY_RUN_READY),
  SEARCH_REINDEX_JOBS_TERMINAL(
      "search.reindex_jobs_terminal", SearchRetentionTargetStatus.DRY_RUN_READY),
  SEARCH_INDEXING_FAILURES_TERMINAL(
      "search.indexing_failures_terminal", SearchRetentionTargetStatus.INVENTORY_ONLY),
  SEARCH_DOCUMENTS_ACTIVE("search.documents_active", SearchRetentionTargetStatus.INVENTORY_ONLY);

  private final String key;
  private final SearchRetentionTargetStatus defaultStatus;

  SearchRetentionTargetKey(String key, SearchRetentionTargetStatus defaultStatus) {
    this.key = key;
    this.defaultStatus = defaultStatus;
  }

  public String key() {
    return key;
  }

  public SearchRetentionTargetStatus defaultStatus() {
    return defaultStatus;
  }

  public static Optional<SearchRetentionTargetKey> fromKey(String raw) {
    if (raw == null) return Optional.empty();
    String trimmed = raw.trim();
    for (SearchRetentionTargetKey value : values()) {
      if (value.key.equals(trimmed)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}

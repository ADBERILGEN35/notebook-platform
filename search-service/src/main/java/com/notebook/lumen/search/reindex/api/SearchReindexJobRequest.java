package com.notebook.lumen.search.reindex.api;

import com.notebook.lumen.search.reindex.domain.SearchReindexMode;
import java.util.UUID;

public record SearchReindexJobRequest(
    SearchReindexMode mode,
    UUID workspaceId,
    UUID notebookId,
    Boolean cleanupOrphans,
    Boolean dryRunCleanup) {
  public boolean cleanupOrphansRequested() {
    return Boolean.TRUE.equals(cleanupOrphans);
  }

  public boolean dryRunCleanupRequested() {
    return Boolean.TRUE.equals(dryRunCleanup);
  }
}

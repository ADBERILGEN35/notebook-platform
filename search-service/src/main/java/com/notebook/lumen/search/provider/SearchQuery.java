package com.notebook.lumen.search.provider;

import java.util.UUID;

public record SearchQuery(
    UUID workspaceId, UUID notebookId, String q, int page, int size, boolean oversample) {
  public int providerSize() {
    int requested = Math.max(1, size);
    if (!oversample) {
      return requested;
    }
    return Math.min(100, requested * 3);
  }

  public int providerPage() {
    return Math.max(0, page);
  }
}

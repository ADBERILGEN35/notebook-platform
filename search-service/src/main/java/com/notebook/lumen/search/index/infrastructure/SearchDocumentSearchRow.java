package com.notebook.lumen.search.index.infrastructure;

import java.time.Instant;
import java.util.UUID;

public interface SearchDocumentSearchRow {
  UUID getWorkspaceId();

  UUID getNotebookId();

  UUID getNoteId();

  String getTitle();

  Instant getNoteUpdatedAt();

  Double getRank();
}

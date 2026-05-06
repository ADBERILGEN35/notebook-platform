package com.notebook.lumen.search.index.infrastructure;

import java.time.Instant;
import java.util.UUID;

public interface SearchOrphanCandidateRow {
  UUID getNoteId();

  UUID getWorkspaceId();

  UUID getNotebookId();

  Instant getIndexedAt();

  Instant getNoteUpdatedAt();

  Instant getArchivedAt();

  Instant getLastSeenReindexAt();
}

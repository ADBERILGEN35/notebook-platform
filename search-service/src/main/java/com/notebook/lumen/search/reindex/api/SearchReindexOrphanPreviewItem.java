package com.notebook.lumen.search.reindex.api;

import java.time.Instant;
import java.util.UUID;

public record SearchReindexOrphanPreviewItem(
    UUID noteId,
    UUID workspaceId,
    UUID notebookId,
    Instant lastSeenReindexAt,
    Instant indexedAt,
    Instant noteUpdatedAt,
    Instant archivedAt) {}

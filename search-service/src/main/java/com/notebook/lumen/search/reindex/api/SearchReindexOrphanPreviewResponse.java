package com.notebook.lumen.search.reindex.api;

import com.notebook.lumen.search.reindex.domain.SearchReindexMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchReindexOrphanPreviewResponse(
    UUID jobId,
    SearchReindexMode mode,
    UUID workspaceId,
    UUID notebookId,
    long orphanCount,
    Instant previewGeneratedAt,
    List<SearchReindexOrphanPreviewItem> items) {}

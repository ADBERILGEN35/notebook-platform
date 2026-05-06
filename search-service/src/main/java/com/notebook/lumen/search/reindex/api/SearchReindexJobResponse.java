package com.notebook.lumen.search.reindex.api;

import com.notebook.lumen.search.reindex.domain.SearchReindexJobStatus;
import com.notebook.lumen.search.reindex.domain.SearchReindexMode;
import java.time.Instant;
import java.util.UUID;

public record SearchReindexJobResponse(
    UUID jobId,
    SearchReindexJobStatus status,
    SearchReindexMode mode,
    UUID workspaceId,
    UUID notebookId,
    long totalScanned,
    long totalIndexed,
    long totalFailed,
    String lastCursor,
    Instant startedAt,
    Instant completedAt,
    Instant failedAt,
    String lastError,
    Instant createdAt,
    Instant updatedAt) {}

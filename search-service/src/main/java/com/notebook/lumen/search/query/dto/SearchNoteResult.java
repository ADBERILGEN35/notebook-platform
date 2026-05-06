package com.notebook.lumen.search.query.dto;

import java.time.Instant;
import java.util.UUID;

public record SearchNoteResult(
    UUID noteId,
    UUID workspaceId,
    UUID notebookId,
    String title,
    String snippet,
    double rank,
    Instant noteUpdatedAt) {}

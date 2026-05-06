package com.notebook.lumen.content.search.source.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record SearchIndexSourceNoteResponse(
    UUID workspaceId,
    UUID notebookId,
    UUID noteId,
    String title,
    JsonNode contentBlocks,
    List<String> tags,
    String notebookName,
    UUID createdBy,
    UUID updatedBy,
    Instant noteCreatedAt,
    Instant noteUpdatedAt,
    Instant archivedAt,
    Integer sourceVersion) {}

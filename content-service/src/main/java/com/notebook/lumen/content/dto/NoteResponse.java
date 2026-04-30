package com.notebook.lumen.content.dto;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record NoteResponse(
    UUID id,
    UUID workspaceId,
    UUID notebookId,
    UUID parentNoteId,
    String title,
    JsonNode contentBlocks,
    int contentSchemaVersion,
    UUID createdBy,
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt) {}

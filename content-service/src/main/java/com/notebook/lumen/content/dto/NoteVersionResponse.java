package com.notebook.lumen.content.dto;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record NoteVersionResponse(
    UUID id,
    UUID workspaceId,
    UUID noteId,
    int versionNumber,
    String title,
    JsonNode contentBlocks,
    int contentSchemaVersion,
    UUID createdBy,
    Instant createdAt) {}

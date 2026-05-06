package com.notebook.lumen.search.index.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record IndexDocumentRequest(
    @NotNull UUID workspaceId,
    UUID notebookId,
    @NotNull UUID noteId,
    @NotBlank @Size(max = 255) String title,
    JsonNode contentBlocks,
    List<String> tags,
    String notebookName,
    UUID createdBy,
    UUID updatedBy,
    Instant noteCreatedAt,
    Instant noteUpdatedAt,
    Instant archivedAt,
    Integer sourceVersion) {}

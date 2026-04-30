package com.notebook.lumen.workspace.dto;

import java.time.Instant;
import java.util.UUID;

public record NotebookResponse(
    UUID id,
    UUID workspaceId,
    String name,
    String icon,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt) {}

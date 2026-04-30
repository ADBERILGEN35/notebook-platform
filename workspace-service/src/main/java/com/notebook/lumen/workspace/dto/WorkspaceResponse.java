package com.notebook.lumen.workspace.dto;

import com.notebook.lumen.workspace.domain.WorkspaceType;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
    UUID id,
    String slug,
    String name,
    WorkspaceType type,
    UUID ownerId,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt) {}

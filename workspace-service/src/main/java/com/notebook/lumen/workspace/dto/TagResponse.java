package com.notebook.lumen.workspace.dto;

import com.notebook.lumen.workspace.domain.TagScope;
import java.time.Instant;
import java.util.UUID;

public record TagResponse(
    UUID id,
    UUID workspaceId,
    String name,
    String color,
    TagScope scope,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt) {}

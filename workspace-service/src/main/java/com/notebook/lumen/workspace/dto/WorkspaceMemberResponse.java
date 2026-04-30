package com.notebook.lumen.workspace.dto;

import com.notebook.lumen.workspace.domain.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberResponse(
    UUID workspaceId,
    UUID userId,
    WorkspaceRole role,
    Instant joinedAt,
    Instant createdAt,
    Instant updatedAt) {}

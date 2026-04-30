package com.notebook.lumen.workspace.dto;

import com.notebook.lumen.workspace.domain.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
    UUID id,
    UUID workspaceId,
    String email,
    WorkspaceRole role,
    Instant expiresAt,
    Instant acceptedAt,
    Instant revokedAt,
    UUID createdBy,
    Instant createdAt,
    String inviteToken,
    String acceptUrl) {}

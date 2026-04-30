package com.notebook.lumen.workspace.dto;

import com.notebook.lumen.workspace.domain.NotebookRole;
import java.time.Instant;
import java.util.UUID;

public record NotebookMemberResponse(
    UUID notebookId,
    UUID workspaceId,
    UUID userId,
    NotebookRole role,
    Instant createdAt,
    Instant updatedAt) {}

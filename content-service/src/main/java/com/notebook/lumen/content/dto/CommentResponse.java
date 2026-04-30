package com.notebook.lumen.content.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
    UUID id,
    UUID workspaceId,
    UUID noteId,
    UUID userId,
    UUID parentCommentId,
    String blockId,
    String content,
    Instant resolvedAt,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}

package com.notebook.lumen.content.dto;

import java.time.Instant;
import java.util.UUID;

public record NoteLinkResponse(
    UUID fromNoteId, UUID toNoteId, UUID workspaceId, Instant createdAt) {}

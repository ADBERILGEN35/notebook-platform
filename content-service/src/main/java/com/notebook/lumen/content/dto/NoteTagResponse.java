package com.notebook.lumen.content.dto;

import java.time.Instant;
import java.util.UUID;

public record NoteTagResponse(UUID noteId, UUID tagId, UUID workspaceId, Instant createdAt) {}

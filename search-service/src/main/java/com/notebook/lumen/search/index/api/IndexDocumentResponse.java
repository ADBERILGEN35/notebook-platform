package com.notebook.lumen.search.index.api;

import java.time.Instant;
import java.util.UUID;

public record IndexDocumentResponse(UUID documentId, UUID noteId, Instant indexedAt) {}

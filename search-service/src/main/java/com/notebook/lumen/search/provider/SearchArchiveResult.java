package com.notebook.lumen.search.provider;

import java.time.Instant;
import java.util.UUID;

public record SearchArchiveResult(UUID noteId, Instant archivedAt, boolean found) {}

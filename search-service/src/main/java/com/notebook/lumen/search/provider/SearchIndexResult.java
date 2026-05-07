package com.notebook.lumen.search.provider;

import java.time.Instant;
import java.util.UUID;

public record SearchIndexResult(UUID noteId, Instant indexedAt, boolean skipped) {}

package com.notebook.lumen.search.reindex.api;

import com.notebook.lumen.search.reindex.domain.SearchReindexMode;
import java.util.UUID;

public record SearchReindexJobRequest(SearchReindexMode mode, UUID workspaceId, UUID notebookId) {}

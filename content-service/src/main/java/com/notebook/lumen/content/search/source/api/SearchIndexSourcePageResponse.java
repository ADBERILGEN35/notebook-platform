package com.notebook.lumen.content.search.source.api;

import java.util.List;

public record SearchIndexSourcePageResponse(
    List<SearchIndexSourceNoteResponse> items, String nextCursor, boolean hasNext) {}

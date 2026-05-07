package com.notebook.lumen.search.provider;

import com.notebook.lumen.search.query.dto.PageResponse;
import com.notebook.lumen.search.query.dto.SearchNoteResult;
import java.time.Instant;
import java.util.UUID;

public interface SearchProvider {
  SearchProviderType type();

  SearchIndexResult upsert(SearchIndexDocument document);

  SearchArchiveResult archive(UUID noteId, Instant archivedAt);

  PageResponse<SearchNoteResult> search(SearchQuery query);

  boolean health();
}

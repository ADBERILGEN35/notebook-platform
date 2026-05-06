package com.notebook.lumen.search.reindex.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import tools.jackson.databind.JsonNode;

@HttpExchange
public interface ContentSearchIndexSourceClient {
  @GetExchange("/internal/search-index-source/notes")
  SearchIndexSourcePageResponse notes(
      @RequestParam(required = false) UUID workspaceId,
      @RequestParam(required = false) UUID notebookId,
      @RequestParam(required = false) String cursor,
      @RequestParam int size);

  record SearchIndexSourcePageResponse(
      List<SearchIndexSourceNoteResponse> items, String nextCursor, boolean hasNext) {}

  record SearchIndexSourceNoteResponse(
      UUID workspaceId,
      UUID notebookId,
      UUID noteId,
      String title,
      JsonNode contentBlocks,
      List<String> tags,
      String notebookName,
      UUID createdBy,
      UUID updatedBy,
      Instant noteCreatedAt,
      Instant noteUpdatedAt,
      Instant archivedAt,
      Integer sourceVersion) {}
}

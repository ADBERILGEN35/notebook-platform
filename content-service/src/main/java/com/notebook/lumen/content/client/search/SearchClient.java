package com.notebook.lumen.content.client.search;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import tools.jackson.databind.JsonNode;

@HttpExchange
public interface SearchClient {
  @PostExchange("/internal/search/documents")
  IndexDocumentResponse upsert(@RequestBody IndexDocumentRequest request);

  @DeleteExchange("/internal/search/documents/{noteId}")
  void archive(@PathVariable UUID noteId);

  record IndexDocumentRequest(
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

  record IndexDocumentResponse(UUID documentId, UUID noteId, Instant indexedAt) {}
}

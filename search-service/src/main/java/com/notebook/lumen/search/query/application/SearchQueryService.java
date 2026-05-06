package com.notebook.lumen.search.query.application;

import com.notebook.lumen.search.index.application.SearchAuditService;
import com.notebook.lumen.search.index.infrastructure.SearchDocumentRepository;
import com.notebook.lumen.search.index.infrastructure.SearchDocumentSearchRow;
import com.notebook.lumen.search.query.dto.PageResponse;
import com.notebook.lumen.search.query.dto.SearchNoteResult;
import com.notebook.lumen.search.shared.config.SearchProperties;
import com.notebook.lumen.search.shared.exception.SearchException;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchQueryService {
  private final SearchDocumentRepository repository;
  private final SearchPermissionService permissionService;
  private final SearchProperties properties;
  private final SearchAuditService auditService;

  public SearchQueryService(
      SearchDocumentRepository repository,
      SearchPermissionService permissionService,
      SearchProperties properties,
      SearchAuditService auditService) {
    this.repository = repository;
    this.permissionService = permissionService;
    this.properties = properties;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public PageResponse<SearchNoteResult> search(
      UUID userId,
      UUID headerWorkspaceId,
      UUID workspaceId,
      String q,
      UUID notebookId,
      int page,
      int size) {
    validateContext(headerWorkspaceId, workspaceId);
    String query = validateQuery(q);
    int safeSize = Math.min(Math.max(size, 1), Math.max(1, properties.maxPageSize()));
    var pageable = PageRequest.of(Math.max(page, 0), safeSize);
    var candidatePage = repository.search(workspaceId, notebookId, query, pageable);
    var permitted =
        candidatePage.getContent().stream()
            .filter(
                document ->
                    permissionService.canRead(userId, workspaceId, document.getNotebookId()))
            .map(document -> toResult(document, query))
            .toList();
    auditService.record(
        "SEARCH_QUERY_EXECUTED",
        workspaceId,
        workspaceId,
        Map.of("qLength", query.length(), "resultCount", permitted.size()));
    return PageResponse.from(new PageImpl<>(permitted, pageable, permitted.size()));
  }

  private void validateContext(UUID headerWorkspaceId, UUID workspaceId) {
    if (headerWorkspaceId != null && !headerWorkspaceId.equals(workspaceId)) {
      throw new SearchException(
          HttpStatus.BAD_REQUEST,
          "INVALID_WORKSPACE_CONTEXT",
          "X-Workspace-Id does not match workspaceId");
    }
  }

  private String validateQuery(String q) {
    if (q == null || q.isBlank()) {
      throw new SearchException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_QUERY", "q is required");
    }
    String trimmed = q.trim();
    if (trimmed.length() < Math.max(1, properties.minQueryLength())) {
      throw new SearchException(
          HttpStatus.BAD_REQUEST, "SEARCH_QUERY_TOO_SHORT", "Search query is too short");
    }
    if (trimmed.length() > Math.max(1, properties.maxQueryLength())) {
      throw new SearchException(
          HttpStatus.BAD_REQUEST, "SEARCH_QUERY_TOO_LONG", "Search query is too long");
    }
    return trimmed;
  }

  private SearchNoteResult toResult(SearchDocumentSearchRow document, String query) {
    return new SearchNoteResult(
        document.getNoteId(),
        document.getWorkspaceId(),
        document.getNotebookId(),
        document.getTitle(),
        snippet(document.getTitle(), query),
        document.getRank() == null ? 0.0d : document.getRank(),
        document.getNoteUpdatedAt());
  }

  private String snippet(String title, String query) {
    if (title == null || title.isBlank()) {
      return "";
    }
    return title.length() <= 180 ? title : title.substring(0, 180);
  }
}

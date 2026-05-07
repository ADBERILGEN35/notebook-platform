package com.notebook.lumen.search.query.application;

import com.notebook.lumen.search.index.application.SearchAuditService;
import com.notebook.lumen.search.provider.SearchProviderRouter;
import com.notebook.lumen.search.provider.SearchQuery;
import com.notebook.lumen.search.query.dto.PageResponse;
import com.notebook.lumen.search.query.dto.SearchNoteResult;
import com.notebook.lumen.search.shared.config.SearchProperties;
import com.notebook.lumen.search.shared.exception.SearchException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchQueryService {
  private final SearchProviderRouter providerRouter;
  private final SearchPermissionService permissionService;
  private final SearchProperties properties;
  private final SearchAuditService auditService;

  public SearchQueryService(
      SearchProviderRouter providerRouter,
      SearchPermissionService permissionService,
      SearchProperties properties,
      SearchAuditService auditService) {
    this.providerRouter = providerRouter;
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
    int safePage = Math.max(page, 0);
    var candidatePage =
        providerRouter.search(new SearchQuery(workspaceId, notebookId, query, safePage, safeSize, true));
    var permitted =
        candidatePage.items().stream()
            .filter(
                document ->
                    permissionService.canRead(userId, workspaceId, document.notebookId()))
            .limit(safeSize)
            .toList();
    auditService.record(
        "SEARCH_QUERY_EXECUTED",
        workspaceId,
        workspaceId,
        Map.of("qLength", query.length(), "resultCount", permitted.size()));
    int totalPages = permitted.size() < safeSize ? safePage + 1 : safePage + 2;
    return new PageResponse<>(
        permitted, safePage, safeSize, permitted.size(), totalPages, permitted.size() < safeSize);
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

}

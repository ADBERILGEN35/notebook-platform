package com.notebook.lumen.search.query.api;

import com.notebook.lumen.search.query.application.SearchQueryService;
import com.notebook.lumen.search.query.dto.PageResponse;
import com.notebook.lumen.search.query.dto.SearchNoteResult;
import com.notebook.lumen.search.shared.exception.SearchException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class SearchController {
  private final SearchQueryService service;

  public SearchController(SearchQueryService service) {
    this.service = service;
  }

  @GetMapping("/notes")
  public PageResponse<SearchNoteResult> notes(
      @RequestHeader("X-User-Id") UUID userId,
      @RequestHeader(value = "X-Workspace-Id", required = false) UUID headerWorkspaceId,
      @RequestParam UUID workspaceId,
      @RequestParam String q,
      @RequestParam(required = false) UUID notebookId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (userId == null) {
      throw new SearchException(
          HttpStatus.UNAUTHORIZED, "SEARCH_ACCESS_DENIED", "User is required");
    }
    return service.search(userId, headerWorkspaceId, workspaceId, q, notebookId, page, size);
  }
}

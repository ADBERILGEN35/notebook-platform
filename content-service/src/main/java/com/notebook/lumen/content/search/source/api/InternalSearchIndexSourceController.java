package com.notebook.lumen.content.search.source.api;

import com.notebook.lumen.content.search.source.application.SearchIndexSourceService;
import com.notebook.lumen.content.search.source.security.SearchIndexSourceAuthorizer;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalSearchIndexSourceController {
  private final SearchIndexSourceAuthorizer authorizer;
  private final SearchIndexSourceService service;

  public InternalSearchIndexSourceController(
      SearchIndexSourceAuthorizer authorizer, SearchIndexSourceService service) {
    this.authorizer = authorizer;
    this.service = service;
  }

  @GetMapping("/internal/search-index-source/notes")
  public SearchIndexSourcePageResponse notes(
      @RequestHeader HttpHeaders headers,
      @RequestParam(required = false) UUID workspaceId,
      @RequestParam(required = false) UUID notebookId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "100") int size) {
    authorizer.authorize(headers.getFirst(SearchIndexSourceAuthorizer.HEADER_NAME));
    return service.notes(workspaceId, notebookId, cursor, size);
  }
}

package com.notebook.lumen.search.index.api;

import com.notebook.lumen.search.index.application.SearchIndexService;
import com.notebook.lumen.search.shared.security.InternalIndexAuthorizer;
import com.notebook.lumen.search.shared.security.InternalPermissionRefreshAuthorizer;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/search")
public class InternalSearchIndexController {
  private final SearchIndexService service;
  private final InternalIndexAuthorizer authorizer;
  private final InternalPermissionRefreshAuthorizer permissionRefreshAuthorizer;

  public InternalSearchIndexController(
      SearchIndexService service,
      InternalIndexAuthorizer authorizer,
      InternalPermissionRefreshAuthorizer permissionRefreshAuthorizer) {
    this.service = service;
    this.authorizer = authorizer;
    this.permissionRefreshAuthorizer = permissionRefreshAuthorizer;
  }

  @PostMapping("/documents")
  public IndexDocumentResponse upsert(
      @RequestHeader HttpHeaders headers, @Valid @RequestBody IndexDocumentRequest request) {
    authorizer.authorize(headers.getFirst(InternalIndexAuthorizer.HEADER_NAME));
    return service.upsert(request);
  }

  @DeleteMapping("/documents/{noteId}")
  public void archive(@RequestHeader HttpHeaders headers, @PathVariable UUID noteId) {
    authorizer.authorize(headers.getFirst(InternalIndexAuthorizer.HEADER_NAME));
    service.archive(noteId);
  }

  @PostMapping("/permissions/notebooks/{notebookId}/refresh")
  public void refreshNotebookPermissions(
      @RequestHeader HttpHeaders headers, @PathVariable UUID notebookId) {
    permissionRefreshAuthorizer.authorize(headers.getFirst(InternalIndexAuthorizer.HEADER_NAME));
    service.refreshNotebookPermissionSnapshot(notebookId);
  }
}

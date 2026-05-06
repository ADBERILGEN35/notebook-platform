package com.notebook.lumen.content.search.outbox.api;

import com.notebook.lumen.content.search.outbox.application.SearchIndexOutboxService;
import com.notebook.lumen.content.search.outbox.application.SearchIndexOutboxStatusView;
import com.notebook.lumen.content.search.outbox.security.SearchOutboxAuthorizer;
import com.notebook.lumen.content.shared.exception.ContentException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalSearchIndexOutboxController {
  private final SearchOutboxAuthorizer authorizer;
  private final SearchIndexOutboxService outboxService;

  public InternalSearchIndexOutboxController(
      SearchOutboxAuthorizer authorizer, SearchIndexOutboxService outboxService) {
    this.authorizer = authorizer;
    this.outboxService = outboxService;
  }

  @PostMapping("/internal/search-index-outbox/reprocess-failed")
  public SearchOutboxReprocessResponse reprocessFailed(
      @RequestHeader(value = SearchOutboxAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(required = false) UUID workspaceId,
      @RequestParam(required = false) UUID noteId,
      @RequestParam(defaultValue = "100") int limit) {
    authorizer.authorize(serviceAuthorization, SearchOutboxAuthorizer.MANAGE_SCOPE);
    if (limit < 1 || limit > 1000) {
      throw new ContentException(
          HttpStatus.BAD_REQUEST,
          "INVALID_SEARCH_OUTBOX_REQUEST",
          "limit must be between 1 and 1000");
    }
    return new SearchOutboxReprocessResponse(
        outboxService.reprocessFailed(workspaceId, noteId, limit));
  }

  @GetMapping("/internal/search-index-outbox/status")
  public SearchIndexOutboxStatusView status(
      @RequestHeader(value = SearchOutboxAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization) {
    authorizer.authorize(serviceAuthorization, SearchOutboxAuthorizer.READ_SCOPE);
    return outboxService.status();
  }

  public record SearchOutboxReprocessResponse(int requeuedCount) {}
}

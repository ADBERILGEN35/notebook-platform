package com.notebook.lumen.search.reindex.api;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtClaims;
import com.notebook.lumen.search.reindex.application.SearchReindexService;
import com.notebook.lumen.search.reindex.security.ReindexAuthorizer;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/search/reindex-jobs")
public class InternalSearchReindexController {
  private final ReindexAuthorizer authorizer;
  private final SearchReindexService service;

  public InternalSearchReindexController(
      ReindexAuthorizer authorizer, SearchReindexService service) {
    this.authorizer = authorizer;
    this.service = service;
  }

  @PostMapping
  public SearchReindexJobResponse create(
      @RequestHeader HttpHeaders headers, @RequestBody SearchReindexJobRequest request) {
    ServiceJwtClaims claims = authorizer.authorize(headers.getFirst(ReindexAuthorizer.HEADER_NAME));
    return service.create(request, claims.serviceName());
  }

  @GetMapping("/{jobId}")
  public SearchReindexJobResponse get(
      @RequestHeader HttpHeaders headers, @PathVariable UUID jobId) {
    authorizer.authorize(headers.getFirst(ReindexAuthorizer.HEADER_NAME));
    return service.get(jobId);
  }

  @PostMapping("/{jobId}/cancel")
  public SearchReindexJobResponse cancel(
      @RequestHeader HttpHeaders headers, @PathVariable UUID jobId) {
    authorizer.authorize(headers.getFirst(ReindexAuthorizer.HEADER_NAME));
    return service.cancel(jobId);
  }
}

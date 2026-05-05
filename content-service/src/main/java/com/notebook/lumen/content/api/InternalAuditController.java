package com.notebook.lumen.content.api;

import com.notebook.lumen.content.audit.AuditAdminAuthorizer;
import com.notebook.lumen.content.audit.AuditEventResponse;
import com.notebook.lumen.content.audit.AuditQueryService;
import com.notebook.lumen.content.dto.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/audit-events")
public class InternalAuditController {
  private final AuditAdminAuthorizer authorizer;
  private final AuditQueryService auditQueryService;

  public InternalAuditController(
      AuditAdminAuthorizer authorizer, AuditQueryService auditQueryService) {
    this.authorizer = authorizer;
    this.auditQueryService = auditQueryService;
  }

  @GetMapping
  public PageResponse<AuditEventResponse> query(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) UUID actorUserId,
      @RequestParam(required = false) UUID workspaceId,
      @RequestParam(required = false) String aggregateType,
      @RequestParam(required = false) UUID aggregateId,
      @RequestParam(required = false) String requestId,
      @RequestParam(required = false) Instant createdFrom,
      @RequestParam(required = false) Instant createdTo,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    authorizer.authorize(serviceAuthorization);
    return auditQueryService.query(
        new AuditQueryService.AuditQuery(
            eventType,
            actorUserId,
            workspaceId,
            aggregateType,
            aggregateId,
            requestId,
            createdFrom,
            createdTo,
            page,
            size,
            sort));
  }
}

package com.notebook.lumen.identity.breakglass;

import com.notebook.lumen.identity.audit.AuditAdminAuthorizer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/break-glass/tokens")
public class InternalBreakGlassTokenController {
  private final AuditAdminAuthorizer authorizer;
  private final BreakGlassAccessEventService eventService;

  public InternalBreakGlassTokenController(
      AuditAdminAuthorizer authorizer, BreakGlassAccessEventService eventService) {
    this.authorizer = authorizer;
    this.eventService = eventService;
  }

  @GetMapping(path = "/{jti}/revoked", produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassReviewDtos.TokenRevokedStatusResponse revokedStatus(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @PathVariable String jti) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_TOKEN_CHECK_SCOPE);
    return eventService.tokenRevokedStatus(jti);
  }
}

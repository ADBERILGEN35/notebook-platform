package com.notebook.lumen.identity.breakglass;

import com.notebook.lumen.identity.admin.InternalAdminChangeRequestController;
import com.notebook.lumen.identity.admin.changerequest.AdminChangeRequestException;
import com.notebook.lumen.identity.audit.AuditAdminAuthorizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/break-glass/sessions")
public class InternalBreakGlassSessionController {
  private final AuditAdminAuthorizer authorizer;
  private final BreakGlassSessionService sessionService;

  public InternalBreakGlassSessionController(
      AuditAdminAuthorizer authorizer, BreakGlassSessionService sessionService) {
    this.authorizer = authorizer;
    this.sessionService = sessionService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassSessionDtos.SessionListResponse list(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(InternalAdminChangeRequestController.HEADER_ADMIN_USER_ID) String adminUserId,
      HttpServletRequest request) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_EVENTS_READ_SCOPE);
    return sessionService.listActiveSessions(parseAdminUserId(adminUserId), request);
  }

  @PostMapping(
      path = "/{jti}/revoke",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassSessionDtos.RevokeSessionResponse revoke(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(InternalAdminChangeRequestController.HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable String jti,
      @Valid @RequestBody BreakGlassSessionDtos.RevokeSessionRequest body,
      HttpServletRequest request) {
    authorizer.authorize(
        serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_EVENTS_REVIEW_SCOPE);
    return sessionService.revokeByJti(
        jti,
        parseAdminUserId(adminUserId),
        body == null ? "" : body.reason(),
        BreakGlassRevocationSource.ADMIN_REVOKE,
        request);
  }

  @PostMapping(
      path = "/revoke-all-active",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassSessionDtos.RevokeAllActiveResponse revokeAllActive(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(InternalAdminChangeRequestController.HEADER_ADMIN_USER_ID) String adminUserId,
      @Valid @RequestBody BreakGlassSessionDtos.RevokeAllActiveRequest body,
      HttpServletRequest request) {
    authorizer.authorize(
        serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_EVENTS_REVIEW_SCOPE);
    return sessionService.revokeAllActive(
        parseAdminUserId(adminUserId),
        body == null ? "" : body.reason(),
        BreakGlassRevocationSource.EMERGENCY_DISABLE,
        request);
  }

  private static UUID parseAdminUserId(String raw) {
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "Invalid X-Admin-User-Id");
    }
  }
}

package com.notebook.lumen.identity.breakglass;

import com.notebook.lumen.identity.admin.InternalAdminChangeRequestController;
import com.notebook.lumen.identity.admin.changerequest.AdminChangeRequestException;
import com.notebook.lumen.identity.audit.AuditAdminAuthorizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/break-glass/events")
public class InternalBreakGlassAdminController {
  private final AuditAdminAuthorizer authorizer;
  private final BreakGlassAccessEventService eventService;

  public InternalBreakGlassAdminController(
      AuditAdminAuthorizer authorizer, BreakGlassAccessEventService eventService) {
    this.authorizer = authorizer;
    this.eventService = eventService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassReviewDtos.EventListResponse list(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "mode", required = false) String mode,
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "25") int size) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_EVENTS_READ_SCOPE);
    return eventService.list(status, mode, parseInstant(from), parseInstant(to), page, size);
  }

  @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassReviewDtos.EventDetailResponse detail(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @PathVariable UUID id) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_EVENTS_READ_SCOPE);
    return eventService.detail(id);
  }

  @PostMapping(
      path = "/{id}/review",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassReviewDtos.EventDetailResponse review(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(InternalAdminChangeRequestController.HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable UUID id,
      @Valid @RequestBody BreakGlassReviewDtos.ReviewRequest body,
      HttpServletRequest request) {
    authorizer.authorize(
        serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_EVENTS_REVIEW_SCOPE);
    return eventService.review(id, parseAdminUserId(adminUserId), body, request);
  }

  @PostMapping(
      path = "/{id}/revoke-token",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassReviewDtos.RevokeTokenResponse revokeToken(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(InternalAdminChangeRequestController.HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable UUID id,
      @Valid @RequestBody BreakGlassReviewDtos.RevokeTokenRequest body,
      HttpServletRequest request) {
    authorizer.authorize(
        serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_EVENTS_REVIEW_SCOPE);
    return eventService.revokeToken(
        id,
        parseAdminUserId(adminUserId),
        body == null ? "" : body.reason(),
        "MANUAL_REVOKE",
        request);
  }

  private static Instant parseInstant(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw.trim());
    } catch (RuntimeException e) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "Invalid timestamp");
    }
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

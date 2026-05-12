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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/break-glass/rotation-events")
public class InternalBreakGlassRotationController {
  private final AuditAdminAuthorizer authorizer;
  private final BreakGlassTokenRotationService rotationService;

  public InternalBreakGlassRotationController(
      AuditAdminAuthorizer authorizer, BreakGlassTokenRotationService rotationService) {
    this.authorizer = authorizer;
    this.rotationService = rotationService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassRotationDtos.RotationEventListResponse list(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "25") int size,
      HttpServletRequest request) {
    authorizer.authorize(
        serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_ROTATION_READ_SCOPE);
    return rotationService.list(status, page, size, request);
  }

  @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassRotationDtos.RotationEventDetail detail(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @PathVariable UUID id,
      HttpServletRequest request) {
    authorizer.authorize(
        serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_ROTATION_READ_SCOPE);
    return rotationService.detail(id, request);
  }

  @PostMapping(
      path = "/{id}/acknowledge",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassRotationDtos.RotationEventDetail acknowledge(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(InternalAdminChangeRequestController.HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable UUID id,
      @Valid @RequestBody BreakGlassRotationDtos.AcknowledgeRequest body,
      HttpServletRequest request) {
    authorizer.authorize(
        serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_ROTATION_MANAGE_SCOPE);
    return rotationService.acknowledge(id, parseAdminUserId(adminUserId), body, request);
  }

  @PostMapping(
      path = "/{id}/verify",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassRotationDtos.RotationEventDetail verify(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(InternalAdminChangeRequestController.HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable UUID id,
      @Valid @RequestBody BreakGlassRotationDtos.VerifyRequest body,
      HttpServletRequest request) {
    authorizer.authorize(
        serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_ROTATION_MANAGE_SCOPE);
    return rotationService.verify(id, parseAdminUserId(adminUserId), body, request);
  }

  @PostMapping(
      path = "/{id}/close",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassRotationDtos.RotationEventDetail close(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(InternalAdminChangeRequestController.HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable UUID id,
      @Valid @RequestBody BreakGlassRotationDtos.CloseRequest body,
      HttpServletRequest request) {
    authorizer.authorize(
        serviceAuthorization, AuditAdminAuthorizer.BREAK_GLASS_ROTATION_MANAGE_SCOPE);
    return rotationService.close(id, parseAdminUserId(adminUserId), body, request);
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

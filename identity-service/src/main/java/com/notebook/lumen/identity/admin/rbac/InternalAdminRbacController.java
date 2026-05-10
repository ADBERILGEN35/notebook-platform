package com.notebook.lumen.identity.admin.rbac;

import com.notebook.lumen.identity.admin.InternalAdminChangeRequestController;
import com.notebook.lumen.identity.admin.changerequest.AdminChangeRequestException;
import com.notebook.lumen.identity.admin.rbac.api.AdminRbacVisibilityDtos;
import com.notebook.lumen.identity.audit.AuditAdminAuthorizer;
import com.notebook.lumen.identity.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/rbac")
public class InternalAdminRbacController {

  private final AuditAdminAuthorizer authorizer;
  private final AdminRbacVisibilityService visibilityService;
  private final AuditService auditService;

  public InternalAdminRbacController(
      AuditAdminAuthorizer authorizer,
      AdminRbacVisibilityService visibilityService,
      AuditService auditService) {
    this.authorizer = authorizer;
    this.visibilityService = visibilityService;
    this.auditService = auditService;
  }

  @GetMapping(path = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminRbacVisibilityDtos.UserListResponse listUsers(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(InternalAdminChangeRequestController.HEADER_ADMIN_USER_ID) String adminUserId,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "role", required = false) String role,
      @RequestParam(name = "permission", required = false) String permission,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "25") int size,
      HttpServletRequest request) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.RBAC_READ_SCOPE);
    UUID actor = UUID.fromString(adminUserId.trim());
    AdminRbacVisibilityDtos.UserListResponse resp =
        visibilityService.listUsers(q, role, permission, page, size);
    auditService.record(
        "ADMIN_RBAC_USERS_VIEWED",
        actor,
        "ADMIN_RBAC",
        null,
        request,
        Map.of(
            "page",
            page,
            "size",
            size,
            "hasQuery",
            q != null && !q.isBlank(),
            "hasRoleFilter",
            role != null && !role.isBlank(),
            "hasPermissionFilter",
            permission != null && !permission.isBlank()));
    return resp;
  }

  @GetMapping(path = "/users/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminRbacVisibilityDtos.UserDetailResponse userDetail(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(InternalAdminChangeRequestController.HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable UUID userId,
      HttpServletRequest request) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.RBAC_READ_SCOPE);
    UUID actor = parseAdminUserId(adminUserId);
    AdminRbacVisibilityDtos.UserDetailResponse resp = visibilityService.userDetail(userId);
    auditService.record(
        "ADMIN_RBAC_USER_DETAIL_VIEWED",
        actor,
        "ADMIN_RBAC",
        userId,
        request,
        Map.of("targetUserId", userId.toString()));
    return resp;
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

package com.notebook.lumen.identity.admin;

import com.notebook.lumen.identity.admin.changerequest.AdminChangeRequestException;
import com.notebook.lumen.identity.admin.changerequest.AdminChangeRequestService;
import com.notebook.lumen.identity.admin.changerequest.api.AdminChangeRequestDtos;
import com.notebook.lumen.identity.admin.gitops.AdminGitOpsPrService;
import com.notebook.lumen.identity.admin.gitops.api.AdminGitOpsDtos;
import com.notebook.lumen.identity.audit.AuditAdminAuthorizer;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/change-requests")
public class InternalAdminChangeRequestController {
  public static final String HEADER_ADMIN_USER_ID = "X-Admin-User-Id";
  public static final String HEADER_ADMIN_USER_EMAIL = "X-Admin-User-Email";

  private final AuditAdminAuthorizer authorizer;
  private final AdminChangeRequestService changeRequestService;
  private final AdminGitOpsPrService adminGitOpsPrService;

  public InternalAdminChangeRequestController(
      AuditAdminAuthorizer authorizer,
      AdminChangeRequestService changeRequestService,
      AdminGitOpsPrService adminGitOpsPrService) {
    this.authorizer = authorizer;
    this.changeRequestService = changeRequestService;
    this.adminGitOpsPrService = adminGitOpsPrService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminChangeRequestDtos.ListResponse list(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestParam(name = "status", required = false) String status) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.CHANGE_REQUEST_SCOPE);
    return changeRequestService.list(status);
  }

  @PostMapping(path = "/validate", produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminChangeRequestDtos.ValidateResponse validate(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(HEADER_ADMIN_USER_ID) String adminUserId,
      @RequestBody AdminChangeRequestDtos.ValidateBody body,
      HttpServletRequest request) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.CHANGE_REQUEST_SCOPE);
    UUID uid = parseAdminUserId(adminUserId);
    changeRequestService.recordValidatedAudit(uid, body, request);
    return changeRequestService.validate(body);
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminChangeRequestDtos.CreateResponse create(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(HEADER_ADMIN_USER_ID) String adminUserId,
      @RequestHeader(value = HEADER_ADMIN_USER_EMAIL, required = false) String adminEmail,
      @RequestHeader(value = "X-Request-Id", required = false) String requestId,
      @RequestBody AdminChangeRequestDtos.CreateBody body,
      HttpServletRequest request) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.CHANGE_REQUEST_SCOPE);
    UUID uid = parseAdminUserId(adminUserId);
    return changeRequestService.create(uid, adminEmail, body, requestId, request);
  }

  @PostMapping(path = "/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable UUID id,
      @RequestParam(name = "globalCancel", defaultValue = "false") boolean globalCancel,
      HttpServletRequest request) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.CHANGE_REQUEST_SCOPE);
    UUID uid = parseAdminUserId(adminUserId);
    changeRequestService.cancel(id, uid, globalCancel, request);
  }

  @PostMapping(path = "/{id}/approve", produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminChangeRequestDtos.ApproveResponse approve(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable UUID id,
      @RequestBody(required = false) AdminChangeRequestDtos.DecisionBody body,
      HttpServletRequest request) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.CHANGE_REQUEST_SCOPE);
    UUID uid = parseAdminUserId(adminUserId);
    return changeRequestService.approve(id, uid, body, request);
  }

  @PostMapping(path = "/{id}/reject", produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminChangeRequestDtos.RejectResponse reject(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable UUID id,
      @RequestBody(required = false) AdminChangeRequestDtos.DecisionBody body,
      HttpServletRequest request) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.CHANGE_REQUEST_SCOPE);
    UUID uid = parseAdminUserId(adminUserId);
    return changeRequestService.reject(id, uid, body, request);
  }

  @PostMapping(
      path = "/{id}/gitops/dry-run",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminGitOpsDtos.DryRunResponse gitopsDryRun(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable UUID id,
      @RequestBody(required = false) AdminGitOpsDtos.DryRunBody body,
      HttpServletRequest request) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.CHANGE_REQUEST_SCOPE);
    UUID uid = parseAdminUserId(adminUserId);
    return adminGitOpsPrService.dryRun(id, body, uid, request);
  }

  @PostMapping(
      path = "/{id}/gitops/create-pr",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public AdminGitOpsDtos.CreatePrResponse gitopsCreatePr(
      @RequestHeader(AuditAdminAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestHeader(HEADER_ADMIN_USER_ID) String adminUserId,
      @PathVariable UUID id,
      @RequestBody AdminGitOpsDtos.CreatePrBody body,
      HttpServletRequest request) {
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.CHANGE_REQUEST_SCOPE);
    UUID uid = parseAdminUserId(adminUserId);
    return adminGitOpsPrService.createPr(id, body, uid, request);
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

package com.notebook.lumen.gateway.admin.enterprise;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.admin.GatewayAdminOperationPermissions;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import com.notebook.lumen.gateway.filter.GatewayHeaders;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
@RestController
public class AdminEnterpriseChangeRequestController {
  private static final Logger log =
      LoggerFactory.getLogger(AdminEnterpriseChangeRequestController.class);
  static final String PATH_PREFIX = "/admin/enterprise/change-requests";

  private final AdminAuthorizationService adminAuthorizationService;
  private final EnterpriseChangeRequestProxyService proxyService;

  public AdminEnterpriseChangeRequestController(
      AdminAuthorizationService adminAuthorizationService,
      EnterpriseChangeRequestProxyService proxyService) {
    this.adminAuthorizationService = adminAuthorizationService;
    this.proxyService = proxyService;
  }

  @GetMapping(path = PATH_PREFIX, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "status", required = false) String status,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ErrorCode> denial = adminAuthorizationService.ensureChangeRequestList(jwt);
    return authorizeAndRun(
        jwt,
        requestId,
        PATH_PREFIX,
        denial,
        permissionDetail(denial, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_LIST, Optional.empty()),
        () -> proxyService.list(status, jwt.getSubject(), jwt.getClaimAsString("email"), requestId, PATH_PREFIX));
  }

  @PostMapping(
      path = PATH_PREFIX + "/validate",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> validate(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<String> op = readOperationType(body);
    Optional<ErrorCode> denial = adminAuthorizationService.ensureChangeRequestValidate(jwt, op);
    return authorizeAndRun(
        jwt,
        requestId,
        PATH_PREFIX + "/validate",
        denial,
        permissionDetail(denial, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CREATE, op),
        () ->
            proxyService.validate(
                body, jwt.getSubject(), jwt.getClaimAsString("email"), requestId, PATH_PREFIX + "/validate"));
  }

  @PostMapping(
      path = PATH_PREFIX,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<String> op = readOperationType(body);
    if (op.isEmpty()) {
      return Mono.just(
          error(
              HttpStatus.BAD_REQUEST,
              ErrorCode.ADMIN_CHANGE_REQUEST_INVALID,
              "operationType is required.",
              PATH_PREFIX,
              requestId,
              null));
    }
    Optional<ErrorCode> denial = adminAuthorizationService.ensureChangeRequestCreate(jwt, op.get());
    return authorizeAndRun(
        jwt,
        requestId,
        PATH_PREFIX,
        denial,
        permissionDetail(denial, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CREATE, op),
        () ->
            proxyService.create(
                body, jwt.getSubject(), jwt.getClaimAsString("email"), requestId, PATH_PREFIX));
  }

  @PostMapping(path = PATH_PREFIX + "/{id}/cancel")
  public Mono<ResponseEntity<Object>> cancel(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    boolean global = adminAuthorizationService.mayCancelAnyPendingChangeRequest(jwt);
    Optional<ErrorCode> denial = adminAuthorizationService.ensureChangeRequestCancel(jwt);
    return authorizeAndRun(
        jwt,
        requestId,
        PATH_PREFIX + "/{id}/cancel",
        denial,
        permissionDetail(
            denial, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CANCEL, Optional.empty()),
        () ->
            proxyService.cancel(
                id,
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                requestId,
                PATH_PREFIX + "/" + id + "/cancel",
                global));
  }

  @PostMapping(
      path = PATH_PREFIX + "/{id}/approve",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Map<String, Object> payload = body == null ? Map.of() : body;
    Optional<ErrorCode> denial = adminAuthorizationService.ensureChangeRequestApprove(jwt);
    return authorizeAndRun(
        jwt,
        requestId,
        PATH_PREFIX + "/{id}/approve",
        denial,
        permissionDetail(denial, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_APPROVE, Optional.empty()),
        () ->
            proxyService.approve(
                id,
                payload,
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                requestId,
                PATH_PREFIX + "/" + id + "/approve"));
  }

  @PostMapping(
      path = PATH_PREFIX + "/{id}/gitops/dry-run",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> gitopsDryRun(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Map<String, Object> payload = body == null ? Map.of() : body;
    Optional<ErrorCode> denial = adminAuthorizationService.ensureChangeRequestGitOpsDryRun(jwt);
    return authorizeAndRun(
        jwt,
        requestId,
        PATH_PREFIX + "/{id}/gitops/dry-run",
        denial,
        permissionDetail(
            denial, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_GITOPS_DRY_RUN, Optional.empty()),
        () ->
            proxyService.gitopsDryRun(
                id,
                payload,
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                requestId,
                PATH_PREFIX + "/" + id + "/gitops/dry-run"));
  }

  @PostMapping(
      path = PATH_PREFIX + "/{id}/gitops/create-pr",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> gitopsCreatePr(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Map<String, Object> payload = body == null ? Map.of() : body;
    Optional<ErrorCode> denial = adminAuthorizationService.ensureChangeRequestGitOpsCreatePr(jwt);
    return authorizeAndRun(
        jwt,
        requestId,
        PATH_PREFIX + "/{id}/gitops/create-pr",
        denial,
        permissionDetail(
            denial, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_GITOPS_CREATE, Optional.empty()),
        () ->
            proxyService.gitopsCreatePr(
                id,
                payload,
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                requestId,
                PATH_PREFIX + "/" + id + "/gitops/create-pr"));
  }

  @PostMapping(
      path = PATH_PREFIX + "/{id}/reject",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Map<String, Object> payload = body == null ? Map.of() : body;
    Optional<ErrorCode> denial = adminAuthorizationService.ensureChangeRequestReject(jwt);
    return authorizeAndRun(
        jwt,
        requestId,
        PATH_PREFIX + "/{id}/reject",
        denial,
        permissionDetail(denial, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_REJECT, Optional.empty()),
        () ->
            proxyService.reject(
                id,
                payload,
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                requestId,
                PATH_PREFIX + "/" + id + "/reject"));
  }

  private Mono<ResponseEntity<Object>> authorizeAndRun(
      Jwt jwt,
      String requestId,
      String path,
      Optional<ErrorCode> denial,
      Map<String, Object> extraDetails,
      java.util.function.Supplier<Mono<ResponseEntity<Object>>> call) {
    if (!adminAuthorizationService.adminWriteFeatureEnabled()) {
      return Mono.just(
          error(
              HttpStatus.NOT_FOUND,
              ErrorCode.ADMIN_WRITE_DISABLED,
              "Enterprise admin write operations are disabled on this gateway.",
              path,
              requestId,
              null));
    }
    if (denial.isPresent()) {
      ErrorCode code = denial.get();
      logDenied(jwt, path, requestId, code);
      String message = denialMessage(code);
      return Mono.just(error(HttpStatus.FORBIDDEN, code, message, path, requestId, extraDetails));
    }
    return call.get();
  }

  private void logDenied(Jwt jwt, String path, String requestId, ErrorCode code) {
    if (code == ErrorCode.ADMIN_WRITE_MFA_REQUIRED) {
      log.warn(
          "admin_write_mfa_required adminUserId={} path={} requestId={}",
          jwt == null ? null : jwt.getSubject(),
          path,
          requestId);
    }
  }

  private static String denialMessage(ErrorCode code) {
    return switch (code) {
      case ADMIN_WRITE_MFA_REQUIRED -> "Admin write requires multi-factor authentication.";
      case ADMIN_PERMISSION_REQUIRED -> "Required admin permission is missing.";
      case ADMIN_OPERATION_PERMISSION_REQUIRED -> "Required permission for this change-request operation is missing.";
      default -> "Admin write access denied.";
    };
  }

  private static Optional<String> readOperationType(Map<String, Object> body) {
    if (body == null) {
      return Optional.empty();
    }
    Object op = body.get("operationType");
    if (op == null) {
      return Optional.empty();
    }
    String s = String.valueOf(op).trim();
    return s.isBlank() ? Optional.empty() : Optional.of(s);
  }

  private static Map<String, Object> permissionDetail(
      Optional<ErrorCode> denial, String defaultPermission, Optional<String> operationType) {
    if (denial.isEmpty()) {
      return null;
    }
    ErrorCode c = denial.get();
    if (c == ErrorCode.ADMIN_OPERATION_PERMISSION_REQUIRED && operationType.isPresent()) {
      String p =
          GatewayAdminOperationPermissions.requiredCreatePermission(operationType.get()).orElse("");
      return Map.of("permission", p, "operationType", operationType.get());
    }
    if (c == ErrorCode.ADMIN_PERMISSION_REQUIRED) {
      return Map.of("permission", defaultPermission);
    }
    return null;
  }

  private ResponseEntity<Object> error(
      HttpStatus status,
      ErrorCode code,
      String message,
      String path,
      String requestId,
      Map<String, Object> details) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse(Instant.now(), status.value(), code.name(), message, path, requestId, details));
  }
}

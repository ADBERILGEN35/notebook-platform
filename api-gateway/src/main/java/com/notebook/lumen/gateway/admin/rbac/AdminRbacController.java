package com.notebook.lumen.gateway.admin.rbac;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import com.notebook.lumen.gateway.filter.GatewayHeaders;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
public class AdminRbacController {
  static final String USERS_PATH = "/admin/rbac/users";
  static final String OVERRIDES_STATUS_PATH = "/admin/rbac/overrides/status";
  static final String OVERRIDES_VALIDATE_PATH = "/admin/rbac/overrides/validate";

  private final AdminAuthorizationService adminAuthorizationService;
  private final AdminRbacProxyService proxyService;

  public AdminRbacController(
      AdminAuthorizationService adminAuthorizationService, AdminRbacProxyService proxyService) {
    this.adminAuthorizationService = adminAuthorizationService;
    this.proxyService = proxyService;
  }

  @GetMapping(path = USERS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> listUsers(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "role", required = false) String role,
      @RequestParam(name = "permission", required = false) String permission,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "25") int size,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ErrorCode> denial = adminAuthorizationService.ensureAdminRbacRead(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbidden(denial.get(), requestId, USERS_PATH));
    }
    return proxyService.listUsers(
        q, role, permission, page, size, jwt.getSubject(), requestId, USERS_PATH);
  }

  @GetMapping(path = USERS_PATH + "/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> userDetail(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID userId,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    String path = USERS_PATH + "/" + userId;
    Optional<ErrorCode> denial = adminAuthorizationService.ensureAdminRbacRead(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbidden(denial.get(), requestId, path));
    }
    return proxyService.userDetail(userId, jwt.getSubject(), requestId, path);
  }

  @GetMapping(path = OVERRIDES_STATUS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> overridesStatus(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ErrorCode> denial = adminAuthorizationService.ensureAdminRbacRead(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbidden(denial.get(), requestId, OVERRIDES_STATUS_PATH));
    }
    return proxyService.overridesStatus(jwt.getSubject(), requestId, OVERRIDES_STATUS_PATH);
  }

  @PostMapping(path = OVERRIDES_VALIDATE_PATH, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> overridesValidate(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ErrorCode> denial = adminAuthorizationService.ensureAdminRbacRead(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbidden(denial.get(), requestId, OVERRIDES_VALIDATE_PATH));
    }
    return proxyService.overridesValidate(body, jwt.getSubject(), requestId, OVERRIDES_VALIDATE_PATH);
  }

  private static ResponseEntity<Object> forbidden(ErrorCode code, String requestId, String path) {
    String message =
        code == ErrorCode.ADMIN_MFA_REQUIRED
            ? "Admin access requires multi-factor authentication."
            : code == ErrorCode.ADMIN_PERMISSION_REQUIRED
                ? "Required admin permission is missing."
                : "Admin access denied.";
    Map<String, Object> details =
        code == ErrorCode.ADMIN_PERMISSION_REQUIRED
            ? Map.of("permission", PlatformAdminRbacConstants.PERM_RBAC_READ)
            : null;
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse(
                Instant.now(), HttpStatus.FORBIDDEN.value(), code.name(), message, path, requestId, details));
  }
}

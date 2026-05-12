package com.notebook.lumen.gateway.admin.scim;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import com.notebook.lumen.gateway.filter.GatewayHeaders;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class AdminScimDiagnosticsController {
  static final String STATUS_PATH = "/admin/identity/scim/compatibility/status";
  static final String RUNS_PATH = "/admin/identity/scim/sync-runs";
  static final String CHECKPOINTS_PATH = "/admin/identity/scim/sync-checkpoints";

  private final AdminAuthorizationService authorizationService;
  private final AdminScimDiagnosticsProxyService proxyService;

  public AdminScimDiagnosticsController(
      AdminAuthorizationService authorizationService,
      AdminScimDiagnosticsProxyService proxyService) {
    this.authorizationService = authorizationService;
    this.proxyService = proxyService;
  }

  @GetMapping(path = STATUS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> compatibilityStatus(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ErrorCode> denial = authorizationService.ensureScimDiagnosticsRead(jwt);
    if (denial.isPresent()) return Mono.just(forbidden(denial.get(), requestId, STATUS_PATH));
    return proxyService.compatibilityStatus(requestId, STATUS_PATH);
  }

  @GetMapping(path = CHECKPOINTS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> syncCheckpoints(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ErrorCode> denial = authorizationService.ensureScimDiagnosticsRead(jwt);
    if (denial.isPresent()) return Mono.just(forbidden(denial.get(), requestId, CHECKPOINTS_PATH));
    return proxyService.syncCheckpoints(requestId, CHECKPOINTS_PATH);
  }

  @GetMapping(path = RUNS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> syncRuns(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String resourceType,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ErrorCode> denial = authorizationService.ensureScimDiagnosticsRead(jwt);
    if (denial.isPresent()) return Mono.just(forbidden(denial.get(), requestId, RUNS_PATH));
    return proxyService.syncRuns(
        provider, resourceType, status, from, to, page, size, requestId, RUNS_PATH);
  }

  private static ResponseEntity<Object> forbidden(ErrorCode code, String requestId, String path) {
    String permission = PlatformAdminRbacConstants.PERM_SCIM_DIAGNOSTICS_READ;
    String message =
        code == ErrorCode.ADMIN_MFA_REQUIRED
            ? "Admin access requires multi-factor authentication."
            : code == ErrorCode.ADMIN_PERMISSION_REQUIRED
                ? "Required admin permission is missing."
                : "Admin access denied.";
    Map<String, Object> details =
        code == ErrorCode.ADMIN_PERMISSION_REQUIRED ? Map.of("permission", permission) : null;
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                code.name(),
                message,
                path,
                requestId,
                details));
  }
}

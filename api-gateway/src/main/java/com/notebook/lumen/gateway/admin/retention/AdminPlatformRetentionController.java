package com.notebook.lumen.gateway.admin.retention;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.config.GatewayPlatformRetentionProperties;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class AdminPlatformRetentionController {
  private static final String TARGETS_PATH = "/admin/retention/platform/targets";
  private static final String PLAN_PATH = "/admin/retention/platform/plan";
  private static final String HOLDS_PATH = "/admin/retention/platform/legal-holds";

  private final AdminAuthorizationService authorizationService;
  private final GatewayPlatformRetentionProperties properties;
  private final AdminPlatformRetentionProxyService proxyService;

  public AdminPlatformRetentionController(
      AdminAuthorizationService authorizationService,
      GatewayPlatformRetentionProperties properties,
      AdminPlatformRetentionProxyService proxyService) {
    this.authorizationService = authorizationService;
    this.properties = properties;
    this.proxyService = proxyService;
  }

  @GetMapping(path = TARGETS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> targets(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ResponseEntity<Object>> preflight = preflight(jwt, requestId, TARGETS_PATH);
    if (preflight.isPresent()) return Mono.just(preflight.get());
    return proxyService.targets(requestId, TARGETS_PATH);
  }

  @GetMapping(path = PLAN_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> plan(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String target,
      @RequestParam(defaultValue = "true") boolean dryRun,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ResponseEntity<Object>> preflight = preflight(jwt, requestId, PLAN_PATH);
    if (preflight.isPresent()) return Mono.just(preflight.get());
    return proxyService.plan(target, dryRun, requestId, PLAN_PATH);
  }

  @GetMapping(path = HOLDS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> legalHolds(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String status,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ResponseEntity<Object>> preflight = preflight(jwt, requestId, HOLDS_PATH);
    if (preflight.isPresent()) return Mono.just(preflight.get());
    return proxyService.legalHolds(status, requestId, HOLDS_PATH);
  }

  @PostMapping(
      path = HOLDS_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> createLegalHold(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ResponseEntity<Object>> preflight = writePreflight(jwt, requestId, HOLDS_PATH);
    if (preflight.isPresent()) return Mono.just(preflight.get());
    return proxyService.createLegalHold(body, jwt.getSubject(), requestId, HOLDS_PATH);
  }

  @PostMapping(
      path = HOLDS_PATH + "/{id}/release",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> releaseLegalHold(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ResponseEntity<Object>> preflight = writePreflight(jwt, requestId, HOLDS_PATH);
    if (preflight.isPresent()) return Mono.just(preflight.get());
    return proxyService.releaseLegalHold(id, body, jwt.getSubject(), requestId, HOLDS_PATH);
  }

  private Optional<ResponseEntity<Object>> preflight(Jwt jwt, String requestId, String path) {
    if (!properties.enabled()) {
      return Optional.of(notFound(requestId, path));
    }
    Optional<ErrorCode> denial = authorizationService.ensurePlatformRetentionRead(jwt);
    return denial.map(errorCode -> forbidden(errorCode, requestId, path, false));
  }

  private Optional<ResponseEntity<Object>> writePreflight(Jwt jwt, String requestId, String path) {
    if (!properties.enabled() || !properties.legalHoldEnabled()) {
      return Optional.of(notFound(requestId, path));
    }
    Optional<ErrorCode> denial = authorizationService.ensurePlatformLegalHoldWrite(jwt);
    if (denial.isPresent()) {
      return Optional.of(forbidden(denial.get(), requestId, path, true));
    }
    if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
      return Optional.of(
          error(
              HttpStatus.BAD_REQUEST,
              ErrorCode.ADMIN_ACCESS_DENIED,
              "Authenticated admin subject is required.",
              requestId,
              path,
              null));
    }
    return Optional.empty();
  }

  private static ResponseEntity<Object> notFound(String requestId, String path) {
    return error(
        HttpStatus.NOT_FOUND,
        ErrorCode.ADMIN_ENTERPRISE_DISABLED,
        "Platform retention governance is disabled.",
        requestId,
        path,
        null);
  }

  private static ResponseEntity<Object> forbidden(
      ErrorCode code, String requestId, String path, boolean write) {
    String message =
        code == ErrorCode.ADMIN_WRITE_MFA_REQUIRED || code == ErrorCode.ADMIN_MFA_REQUIRED
            ? "Admin access requires multi-factor authentication."
            : code == ErrorCode.ADMIN_PERMISSION_REQUIRED
                ? "Required admin permission is missing."
                : "Admin access denied.";
    Map<String, Object> details =
        code == ErrorCode.ADMIN_PERMISSION_REQUIRED
            ? Map.of(
                "permission",
                write
                    ? PlatformAdminRbacConstants.PERM_RETENTION_LEGAL_HOLD_WRITE
                    : PlatformAdminRbacConstants.PERM_RETENTION_READ)
            : null;
    return error(HttpStatus.FORBIDDEN, code, message, requestId, path, details);
  }

  private static ResponseEntity<Object> error(
      HttpStatus status,
      ErrorCode code,
      String message,
      String requestId,
      String path,
      Map<String, Object> details) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse(
                Instant.now(), status.value(), code.name(), message, path, requestId, details));
  }
}

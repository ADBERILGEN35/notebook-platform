package com.notebook.lumen.gateway.admin.breakglass;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class BreakGlassSessionsController {
  private static final String BASE = "/admin/security/break-glass/sessions";

  private final AdminAuthorizationService authz;
  private final BreakGlassSessionsProxyService proxy;

  public BreakGlassSessionsController(
      AdminAuthorizationService authz, BreakGlassSessionsProxyService proxy) {
    this.authz = authz;
    this.proxy = proxy;
  }

  @GetMapping(path = BASE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ErrorCode> denial = authz.ensureBreakGlassRead(jwt);
    if (denial.isPresent()) {
      return Mono.just(
          forbidden(
              denial.get(), requestId, BASE, PlatformAdminRbacConstants.PERM_BREAK_GLASS_READ));
    }
    return proxy.list(jwt.getSubject(), requestId, BASE);
  }

  @PostMapping(
      path = BASE + "/{jti}/revoke",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> revoke(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String jti,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    String path = BASE + "/" + jti + "/revoke";
    Optional<ErrorCode> denial = authz.ensureBreakGlassRevoke(jwt);
    if (denial.isPresent()) {
      return Mono.just(
          forbidden(
              denial.get(), requestId, path, PlatformAdminRbacConstants.PERM_BREAK_GLASS_REVOKE));
    }
    return proxy.revoke(jti, body, jwt.getSubject(), requestId, path);
  }

  @PostMapping(
      path = BASE + "/revoke-all-active",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> revokeAllActive(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    String path = BASE + "/revoke-all-active";
    Optional<ErrorCode> denial = authz.ensureBreakGlassRevoke(jwt);
    if (denial.isPresent()) {
      return Mono.just(
          forbidden(
              denial.get(), requestId, path, PlatformAdminRbacConstants.PERM_BREAK_GLASS_REVOKE));
    }
    return proxy.revokeAllActive(body, jwt.getSubject(), requestId, path);
  }

  private static ResponseEntity<Object> forbidden(
      ErrorCode code, String requestId, String path, String permission) {
    String message =
        code == ErrorCode.ADMIN_MFA_REQUIRED || code == ErrorCode.ADMIN_WRITE_MFA_REQUIRED
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

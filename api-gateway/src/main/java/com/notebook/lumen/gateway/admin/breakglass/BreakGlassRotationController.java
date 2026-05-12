package com.notebook.lumen.gateway.admin.breakglass;

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
public class BreakGlassRotationController {
  private static final String BASE = "/admin/break-glass/rotation-events";

  private final AdminAuthorizationService authz;
  private final BreakGlassRotationProxyService proxy;

  public BreakGlassRotationController(
      AdminAuthorizationService authz, BreakGlassRotationProxyService proxy) {
    this.authz = authz;
    this.proxy = proxy;
  }

  @GetMapping(path = BASE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "25") int size,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Optional<ErrorCode> denial = authz.ensureBreakGlassRotationRead(jwt);
    if (denial.isPresent())
      return Mono.just(
          forbidden(
              denial.get(),
              requestId,
              BASE,
              PlatformAdminRbacConstants.PERM_BREAK_GLASS_ROTATION_READ));
    return proxy.list(status, page, size, jwt.getSubject(), requestId, BASE);
  }

  @GetMapping(path = BASE + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> detail(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    String path = BASE + "/" + id;
    Optional<ErrorCode> denial = authz.ensureBreakGlassRotationRead(jwt);
    if (denial.isPresent())
      return Mono.just(
          forbidden(
              denial.get(),
              requestId,
              path,
              PlatformAdminRbacConstants.PERM_BREAK_GLASS_ROTATION_READ));
    return proxy.detail(id, jwt.getSubject(), requestId, path);
  }

  @PostMapping(
      path = BASE + "/{id}/acknowledge",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> acknowledge(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    String path = BASE + "/" + id + "/acknowledge";
    Optional<ErrorCode> denial = authz.ensureBreakGlassRotationManage(jwt);
    if (denial.isPresent())
      return Mono.just(
          forbidden(
              denial.get(),
              requestId,
              path,
              PlatformAdminRbacConstants.PERM_BREAK_GLASS_ROTATION_MANAGE));
    return proxy.acknowledge(id, body, jwt.getSubject(), requestId, path);
  }

  @PostMapping(
      path = BASE + "/{id}/verify",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> verify(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    String path = BASE + "/" + id + "/verify";
    Optional<ErrorCode> denial = authz.ensureBreakGlassRotationManage(jwt);
    if (denial.isPresent())
      return Mono.just(
          forbidden(
              denial.get(),
              requestId,
              path,
              PlatformAdminRbacConstants.PERM_BREAK_GLASS_ROTATION_MANAGE));
    return proxy.verify(id, body, jwt.getSubject(), requestId, path);
  }

  @PostMapping(
      path = BASE + "/{id}/close",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> close(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    String path = BASE + "/" + id + "/close";
    Optional<ErrorCode> denial = authz.ensureBreakGlassRotationManage(jwt);
    if (denial.isPresent())
      return Mono.just(
          forbidden(
              denial.get(),
              requestId,
              path,
              PlatformAdminRbacConstants.PERM_BREAK_GLASS_ROTATION_MANAGE));
    return proxy.close(id, body, jwt.getSubject(), requestId, path);
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

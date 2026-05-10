package com.notebook.lumen.gateway.admin.notifications;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import com.notebook.lumen.gateway.filter.GatewayHeaders;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController
public class AdminNotificationRetentionController {
  private static final Logger log = LoggerFactory.getLogger(AdminNotificationRetentionController.class);

  private final AdminAuthorizationService adminAuthorizationService;
  private final AdminNotificationRetentionProxyService proxyService;

  public AdminNotificationRetentionController(
      AdminAuthorizationService adminAuthorizationService,
      AdminNotificationRetentionProxyService proxyService) {
    this.adminAuthorizationService = adminAuthorizationService;
    this.proxyService = proxyService;
  }

  @GetMapping(path = "/admin/notifications/retention/plan", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> plan(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "true") boolean dryRun,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    if (!adminAuthorizationService.enterpriseFeatureEnabled()) {
      return Mono.just(
          error(
              HttpStatus.NOT_FOUND,
              ErrorCode.ADMIN_ENTERPRISE_DISABLED,
              "Enterprise admin console is disabled on this gateway.",
              requestId,
              "/admin/notifications/retention/plan",
              null));
    }
    Optional<ErrorCode> denial =
        adminAuthorizationService.ensureNotificationRetentionRead(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbidden(jwt, denial.get(), requestId, "/admin/notifications/retention/plan"));
    }
    return proxyService
        .plan(dryRun)
        .map(
            body -> {
              log.info(
                  "admin_notification_retention_plan_viewed adminUserId={} dryRun={} requestId={}",
                  jwt == null ? null : jwt.getSubject(),
                  dryRun,
                  requestId);
              return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).<Object>body(body);
            })
        .onErrorResume(
            WebClientResponseException.class,
            ex ->
                Mono.just(
                    error(
                        HttpStatus.BAD_GATEWAY,
                        ErrorCode.AUDIT_PROXY_REQUEST_FAILED,
                        "Notification retention service unavailable.",
                        requestId,
                        "/admin/notifications/retention/plan",
                        Map.of("status", ex.getStatusCode().value()))));
  }

  @PostMapping(
      path = "/admin/notifications/retention/run",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> run(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    if (!adminAuthorizationService.enterpriseFeatureEnabled()) {
      return Mono.just(
          error(
              HttpStatus.NOT_FOUND,
              ErrorCode.ADMIN_ENTERPRISE_DISABLED,
              "Enterprise admin console is disabled on this gateway.",
              requestId,
              "/admin/notifications/retention/run",
              null));
    }
    boolean dryRun = effectiveDryRun(body);
    Optional<ErrorCode> denial =
        dryRun
            ? adminAuthorizationService.ensureNotificationRetentionRead(jwt)
            : adminAuthorizationService.ensureNotificationRetentionRun(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbidden(jwt, denial.get(), requestId, "/admin/notifications/retention/run"));
    }
    String actorUserId = jwt == null ? null : jwt.getSubject();
    if (!dryRun && (actorUserId == null || actorUserId.isBlank())) {
      return Mono.just(
          error(
              HttpStatus.BAD_REQUEST,
              ErrorCode.ADMIN_ACCESS_DENIED,
              "Authenticated admin subject is required for retention purge.",
              requestId,
              "/admin/notifications/retention/run",
              null));
    }
    log.info(
        "admin_notification_retention_run adminUserId={} dryRun={} requestId={}",
        actorUserId,
        dryRun,
        requestId);
    return proxyService
        .run(body, !dryRun, actorUserId)
        .map(b -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).<Object>body(b))
        .onErrorResume(
            WebClientResponseException.class,
            ex ->
                Mono.just(
                    error(
                        HttpStatus.BAD_GATEWAY,
                        ErrorCode.AUDIT_PROXY_REQUEST_FAILED,
                        "Notification retention service unavailable.",
                        requestId,
                        "/admin/notifications/retention/run",
                        Map.of("status", ex.getStatusCode().value()))));
  }

  private static boolean effectiveDryRun(Map<String, Object> body) {
    if (body == null || !body.containsKey("dryRun") || body.get("dryRun") == null) {
      return true;
    }
    Object v = body.get("dryRun");
    if (v instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(v));
  }

  private ResponseEntity<Object> forbidden(Jwt jwt, ErrorCode code, String requestId, String path) {
    if (code == ErrorCode.ADMIN_WRITE_MFA_REQUIRED || code == ErrorCode.ADMIN_MFA_REQUIRED) {
      log.warn(
          "admin_mfa_required_blocked adminUserId={} endpoint={} requestId={}",
          jwt == null ? null : jwt.getSubject(),
          path,
          requestId);
    }
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
                path.endsWith("run")
                    ? PlatformAdminRbacConstants.PERM_NOTIFICATIONS_RETENTION_RUN
                    : PlatformAdminRbacConstants.PERM_NOTIFICATIONS_RETENTION_READ)
            : null;
    return error(HttpStatus.FORBIDDEN, code, message, requestId, path, details);
  }

  private ResponseEntity<Object> error(
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
                java.time.Instant.now(),
                status.value(),
                code.name(),
                message,
                path,
                requestId,
                details));
  }
}

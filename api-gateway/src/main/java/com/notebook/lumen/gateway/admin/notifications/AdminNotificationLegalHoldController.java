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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController
public class AdminNotificationLegalHoldController {
  private static final Logger log = LoggerFactory.getLogger(AdminNotificationLegalHoldController.class);

  private final AdminAuthorizationService adminAuthorizationService;
  private final AdminNotificationLegalHoldProxyService proxyService;

  public AdminNotificationLegalHoldController(
      AdminAuthorizationService adminAuthorizationService,
      AdminNotificationLegalHoldProxyService proxyService) {
    this.adminAuthorizationService = adminAuthorizationService;
    this.proxyService = proxyService;
  }

  @GetMapping(path = "/admin/notifications/legal-holds", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String status,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    if (!adminAuthorizationService.enterpriseFeatureEnabled()) {
      return Mono.just(
          notFound(
              ErrorCode.ADMIN_ENTERPRISE_DISABLED,
              "Enterprise admin console is disabled on this gateway.",
              requestId));
    }
    Optional<ErrorCode> denial = adminAuthorizationService.ensureNotificationLegalHoldRead(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbidden(jwt, denial.get(), requestId, false));
    }
    String qs = status == null || status.isBlank() ? "" : "status=" + urlEncode(status);
    return proxyService
        .list(qs)
        .map(
            body -> {
              log.info(
                  "admin_notification_legal_hold_list adminUserId={} requestId={}",
                  jwt == null ? null : jwt.getSubject(),
                  requestId);
              return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).<Object>body(body);
            })
        .onErrorResume(
            WebClientResponseException.class,
            ex ->
                Mono.just(
                    badGateway(
                        "Notification legal hold service unavailable.",
                        requestId,
                        Map.of("status", ex.getStatusCode().value()))));
  }

  @PostMapping(
      path = "/admin/notifications/legal-holds",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    if (!adminAuthorizationService.enterpriseFeatureEnabled()) {
      return Mono.just(
          notFound(
              ErrorCode.ADMIN_ENTERPRISE_DISABLED,
              "Enterprise admin console is disabled on this gateway.",
              requestId));
    }
    Optional<ErrorCode> denial = adminAuthorizationService.ensureNotificationLegalHoldWrite(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbidden(jwt, denial.get(), requestId, true));
    }
    String actorUserId = jwt == null ? null : jwt.getSubject();
    if (actorUserId == null || actorUserId.isBlank()) {
      return Mono.just(
          error(
              HttpStatus.BAD_REQUEST,
              ErrorCode.ADMIN_ACCESS_DENIED,
              "Authenticated admin subject is required.",
              requestId,
              "/admin/notifications/legal-holds",
              null));
    }
    String email = jwt.getClaimAsString("email");
    log.info("admin_notification_legal_hold_create adminUserId={} requestId={}", actorUserId, requestId);
    return proxyService
        .create(body, actorUserId, email)
        .map(b -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).<Object>body(b))
        .onErrorResume(
            WebClientResponseException.class,
            ex ->
                Mono.just(
                    badGateway(
                        "Notification legal hold service unavailable.",
                        requestId,
                        Map.of("status", ex.getStatusCode().value()))));
  }

  @PostMapping(
      path = "/admin/notifications/legal-holds/{id}/release",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> release(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable("id") String id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    if (!adminAuthorizationService.enterpriseFeatureEnabled()) {
      return Mono.just(
          notFound(
              ErrorCode.ADMIN_ENTERPRISE_DISABLED,
              "Enterprise admin console is disabled on this gateway.",
              requestId));
    }
    Optional<ErrorCode> denial = adminAuthorizationService.ensureNotificationLegalHoldWrite(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbidden(jwt, denial.get(), requestId, true));
    }
    String actorUserId = jwt == null ? null : jwt.getSubject();
    if (actorUserId == null || actorUserId.isBlank()) {
      return Mono.just(
          error(
              HttpStatus.BAD_REQUEST,
              ErrorCode.ADMIN_ACCESS_DENIED,
              "Authenticated admin subject is required.",
              requestId,
              "/admin/notifications/legal-holds",
              null));
    }
    log.info(
        "admin_notification_legal_hold_release adminUserId={} holdId={} requestId={}",
        actorUserId,
        id,
        requestId);
    return proxyService
        .release(id, body, actorUserId)
        .map(b -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).<Object>body(b))
        .onErrorResume(
            WebClientResponseException.class,
            ex ->
                Mono.just(
                    badGateway(
                        "Notification legal hold service unavailable.",
                        requestId,
                        Map.of("status", ex.getStatusCode().value()))));
  }

  private static String urlEncode(String v) {
    return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
  }

  private ResponseEntity<Object> forbidden(Jwt jwt, ErrorCode code, String requestId, boolean write) {
    if (code == ErrorCode.ADMIN_WRITE_MFA_REQUIRED || code == ErrorCode.ADMIN_MFA_REQUIRED) {
      log.warn(
          "admin_mfa_required_blocked adminUserId={} endpoint=legal-holds requestId={}",
          jwt == null ? null : jwt.getSubject(),
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
                write
                    ? PlatformAdminRbacConstants.PERM_NOTIFICATIONS_LEGAL_HOLD_WRITE
                    : PlatformAdminRbacConstants.PERM_NOTIFICATIONS_LEGAL_HOLD_READ)
            : null;
    return error(
        HttpStatus.FORBIDDEN,
        code,
        message,
        requestId,
        "/admin/notifications/legal-holds",
        details);
  }

  private ResponseEntity<Object> notFound(ErrorCode code, String message, String requestId) {
    return error(HttpStatus.NOT_FOUND, code, message, requestId, "/admin/notifications/legal-holds", null);
  }

  private ResponseEntity<Object> badGateway(String message, String requestId, Map<String, Object> details) {
    return error(
        HttpStatus.BAD_GATEWAY, ErrorCode.AUDIT_PROXY_REQUEST_FAILED, message, requestId, null, details);
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
                path == null ? "/admin/notifications/legal-holds" : path,
                requestId,
                details));
  }
}

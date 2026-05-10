package com.notebook.lumen.gateway.admin.notifications;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import com.notebook.lumen.gateway.filter.GatewayHeaders;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController
public class AdminNotificationAnalyticsController {
  private static final Logger log = LoggerFactory.getLogger(AdminNotificationAnalyticsController.class);

  private final AdminAuthorizationService adminAuthorizationService;
  private final AdminNotificationAnalyticsProxyService proxyService;

  public AdminNotificationAnalyticsController(
      AdminAuthorizationService adminAuthorizationService,
      AdminNotificationAnalyticsProxyService proxyService) {
    this.adminAuthorizationService = adminAuthorizationService;
    this.proxyService = proxyService;
  }

  @GetMapping(path = "/admin/notifications/analytics/summary", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> summary(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(name = "bucket", required = false) String bucket,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    if (!adminAuthorizationService.enterpriseFeatureEnabled()) {
      return Mono.just(
          error(
              HttpStatus.NOT_FOUND,
              ErrorCode.ADMIN_ENTERPRISE_DISABLED,
              "Enterprise admin console is disabled on this gateway.",
              requestId,
              "/admin/notifications/analytics/summary",
              null));
    }
    Optional<ErrorCode> denial =
        adminAuthorizationService.ensureAdminPermission(
            jwt, PlatformAdminRbacConstants.PERM_NOTIFICATIONS_ANALYTICS_READ);
    if (denial.isPresent()) {
      return Mono.just(forbidden(jwt, denial.get(), requestId));
    }

    return proxyService
        .fetchSummary(from, to, bucket)
        .map(
            body -> {
              log.info(
                  "admin_notification_analytics_viewed adminUserId={} from={} to={} bucket={} requestId={}",
                  jwt == null ? null : jwt.getSubject(),
                  from,
                  to,
                  bucket,
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
                        "Notification analytics service unavailable.",
                        requestId,
                        "/admin/notifications/analytics/summary",
                        Map.of("status", ex.getStatusCode().value()))));
  }

  private ResponseEntity<Object> forbidden(Jwt jwt, ErrorCode code, String requestId) {
    if (code == ErrorCode.ADMIN_MFA_REQUIRED) {
      log.warn(
          "admin_mfa_required_blocked adminUserId={} endpoint={} requestId={}",
          jwt == null ? null : jwt.getSubject(),
          "/admin/notifications/analytics/summary",
          requestId);
    }
    String message =
        code == ErrorCode.ADMIN_MFA_REQUIRED
            ? "Admin access requires multi-factor authentication."
            : code == ErrorCode.ADMIN_PERMISSION_REQUIRED
                ? "Required admin permission is missing."
                : "Admin access denied.";
    Map<String, Object> details =
        code == ErrorCode.ADMIN_PERMISSION_REQUIRED
            ? Map.of("permission", PlatformAdminRbacConstants.PERM_NOTIFICATIONS_ANALYTICS_READ)
            : null;
    return error(HttpStatus.FORBIDDEN, code, message, requestId, "/admin/notifications/analytics/summary", details);
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

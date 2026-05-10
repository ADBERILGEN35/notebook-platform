package com.notebook.lumen.gateway.admin.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import com.notebook.lumen.gateway.filter.GatewayHeaders;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController
public class AdminNotificationDeadLetterController {
  private static final Logger log = LoggerFactory.getLogger(AdminNotificationDeadLetterController.class);

  private final AdminAuthorizationService adminAuthorizationService;
  private final AdminNotificationDeadLetterProxyService proxyService;

  public AdminNotificationDeadLetterController(
      AdminAuthorizationService adminAuthorizationService,
      AdminNotificationDeadLetterProxyService proxyService) {
    this.adminAuthorizationService = adminAuthorizationService;
    this.proxyService = proxyService;
  }

  @GetMapping(path = "/admin/notifications/dead-letter", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "fanout") String source,
      @RequestParam(defaultValue = "DEAD") String status,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String createdFrom,
      @RequestParam(required = false) String createdTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) String sort,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    if (!adminAuthorizationService.enterpriseFeatureEnabled()) {
      return Mono.just(
          notFound(
              ErrorCode.ADMIN_ENTERPRISE_DISABLED,
              "Enterprise admin console is disabled on this gateway.",
              requestId));
    }
    Optional<ErrorCode> denial =
        adminAuthorizationService.ensureNotificationDeadLetterRead(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbiddenRead(jwt, denial.get(), requestId));
    }
    StringBuilder q = new StringBuilder();
    q.append("source=").append(urlEncode(source));
    q.append("&status=").append(urlEncode(status));
    if (eventType != null && !eventType.isBlank()) {
      q.append("&eventType=").append(urlEncode(eventType));
    }
    if (createdFrom != null && !createdFrom.isBlank()) {
      q.append("&createdFrom=").append(urlEncode(createdFrom));
    }
    if (createdTo != null && !createdTo.isBlank()) {
      q.append("&createdTo=").append(urlEncode(createdTo));
    }
    q.append("&page=").append(page);
    q.append("&size=").append(size);
    if (sort != null && !sort.isBlank()) {
      q.append("&sort=").append(urlEncode(sort));
    }
    String qs = q.toString();
    return proxyService
        .list(qs)
        .map(
            body -> {
              log.info(
                  "admin_notification_dead_letter_viewed adminUserId={} requestId={} query={}",
                  jwt == null ? null : jwt.getSubject(),
                  requestId,
                  qs);
              return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).<Object>body(body);
            })
        .onErrorResume(
            WebClientResponseException.class,
            ex ->
                Mono.just(
                    badGateway(
                        "Notification dead-letter service unavailable.",
                        requestId,
                        Map.of("status", ex.getStatusCode().value()))));
  }

  @PostMapping(
      path = "/admin/notifications/dead-letter/{id}/requeue/dry-run",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> dryRun(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    if (!adminAuthorizationService.enterpriseFeatureEnabled()) {
      return Mono.just(
          notFound(
              ErrorCode.ADMIN_ENTERPRISE_DISABLED,
              "Enterprise admin console is disabled on this gateway.",
              requestId));
    }
    Optional<ErrorCode> denial =
        adminAuthorizationService.ensureNotificationDeadLetterRead(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbiddenRead(jwt, denial.get(), requestId));
    }
    return proxyService
        .dryRun(id)
        .map(
            body -> {
              log.info(
                  "admin_notification_dead_letter_requeue_dry_run adminUserId={} id={} requestId={}",
                  jwt == null ? null : jwt.getSubject(),
                  id,
                  requestId);
              return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).<Object>body(body);
            })
        .onErrorResume(
            WebClientResponseException.class,
            ex ->
                Mono.just(
                    badGateway(
                        "Notification dead-letter service unavailable.",
                        requestId,
                        Map.of("status", ex.getStatusCode().value()))));
  }

  public record RequeueBody(String idempotencyKey, String reason) {}

  @PostMapping(
      path = "/admin/notifications/dead-letter/{id}/requeue",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> requeue(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody RequeueBody body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    if (!adminAuthorizationService.enterpriseFeatureEnabled()) {
      return Mono.just(
          notFound(
              ErrorCode.ADMIN_ENTERPRISE_DISABLED,
              "Enterprise admin console is disabled on this gateway.",
              requestId));
    }
    Optional<ErrorCode> denial =
        adminAuthorizationService.ensureNotificationDeadLetterRequeue(jwt);
    if (denial.isPresent()) {
      return Mono.just(forbiddenRequeue(jwt, denial.get(), requestId));
    }
    if (body == null
        || body.idempotencyKey() == null
        || body.idempotencyKey().isBlank()
        || body.reason() == null
        || body.reason().isBlank()) {
      return Mono.just(
          ResponseEntity.status(HttpStatus.BAD_REQUEST)
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  new ErrorResponse(
                      java.time.Instant.now(),
                      400,
                      ErrorCode.ADMIN_OPERATION_NOT_ALLOWED.name(),
                      "idempotencyKey and reason are required",
                      "/admin/notifications/dead-letter/" + id + "/requeue",
                      requestId,
                      null)));
    }
    String actor = jwt == null ? "" : jwt.getSubject();
    return proxyService
        .requeue(id, body.idempotencyKey().trim(), body.reason().trim(), actor)
        .map(
            node -> {
              log.info(
                  "admin_notification_dead_letter_requeued adminUserId={} id={} requestId={}",
                  jwt == null ? null : jwt.getSubject(),
                  id,
                  requestId);
              return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).<Object>body(node);
            })
        .onErrorResume(
            WebClientResponseException.class,
            ex ->
                Mono.just(
                    badGateway(
                        "Notification dead-letter service unavailable.",
                        requestId,
                        Map.of("status", ex.getStatusCode().value()))));
  }

  private static String urlEncode(String s) {
    return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
  }

  private ResponseEntity<Object> forbiddenRead(Jwt jwt, ErrorCode code, String requestId) {
    return forbidden(jwt, code, requestId, PlatformAdminRbacConstants.PERM_NOTIFICATIONS_DEAD_LETTER_READ);
  }

  private ResponseEntity<Object> forbiddenRequeue(Jwt jwt, ErrorCode code, String requestId) {
    if (code == ErrorCode.ADMIN_WRITE_MFA_REQUIRED) {
      log.warn(
          "admin_write_mfa_required_blocked adminUserId={} endpoint=dead-letter-requeue requestId={}",
          jwt == null ? null : jwt.getSubject(),
          requestId);
    }
    String message =
        switch (code) {
          case ADMIN_MFA_REQUIRED -> "Admin access requires multi-factor authentication.";
          case ADMIN_WRITE_MFA_REQUIRED -> "Admin write requires multi-factor authentication.";
          case ADMIN_PERMISSION_REQUIRED -> "Required admin permission is missing.";
          default -> "Admin access denied.";
        };
    Map<String, Object> details =
        code == ErrorCode.ADMIN_PERMISSION_REQUIRED
            ? Map.of("permission", PlatformAdminRbacConstants.PERM_NOTIFICATIONS_DEAD_LETTER_REQUEUE)
            : null;
    return error(HttpStatus.FORBIDDEN, code, message, requestId, "/admin/notifications/dead-letter", details);
  }

  private ResponseEntity<Object> forbidden(Jwt jwt, ErrorCode code, String requestId, String permission) {
    if (code == ErrorCode.ADMIN_MFA_REQUIRED) {
      log.warn(
          "admin_mfa_required_blocked adminUserId={} endpoint=dead-letter requestId={}",
          jwt == null ? null : jwt.getSubject(),
          requestId);
    }
    String message =
        code == ErrorCode.ADMIN_MFA_REQUIRED
            ? "Admin access requires multi-factor authentication."
            : code == ErrorCode.ADMIN_PERMISSION_REQUIRED
                ? "Required admin permission is missing."
                : "Admin access denied.";
    Map<String, Object> details =
        code == ErrorCode.ADMIN_PERMISSION_REQUIRED ? Map.of("permission", permission) : null;
    return error(HttpStatus.FORBIDDEN, code, message, requestId, "/admin/notifications/dead-letter", details);
  }

  private ResponseEntity<Object> notFound(ErrorCode code, String message, String requestId) {
    return error(HttpStatus.NOT_FOUND, code, message, requestId, "/admin/notifications/dead-letter", null);
  }

  private ResponseEntity<Object> badGateway(
      String message, String requestId, Map<String, Object> details) {
    return error(
        HttpStatus.BAD_GATEWAY,
        ErrorCode.AUDIT_PROXY_REQUEST_FAILED,
        message,
        requestId,
        "/admin/notifications/dead-letter",
        details);
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

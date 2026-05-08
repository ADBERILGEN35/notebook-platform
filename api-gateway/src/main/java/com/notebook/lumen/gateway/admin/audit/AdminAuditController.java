package com.notebook.lumen.gateway.admin.audit;

import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import com.notebook.lumen.gateway.filter.GatewayHeaders;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
import reactor.core.publisher.Mono;

@RestController
public class AdminAuditController {
  private static final Logger log = LoggerFactory.getLogger(AdminAuditController.class);

  private final AdminAuthorizationService adminAuthorizationService;
  private final AuditProxyService auditProxyService;

  public AdminAuditController(
      AdminAuthorizationService adminAuthorizationService, AuditProxyService auditProxyService) {
    this.adminAuthorizationService = adminAuthorizationService;
    this.auditProxyService = auditProxyService;
  }

  @GetMapping(path = "/admin/audit-events", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> queryAuditEvents(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "source", required = false) String source,
      @RequestParam Map<String, String> allQueryParams,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    if (!adminAuthorizationService.adminFeatureEnabled()) {
      return Mono.just(error(HttpStatus.NOT_FOUND, ErrorCode.ADMIN_AUDIT_DISABLED, "Admin audit is disabled", requestId));
    }
    if (!adminAuthorizationService.isAdmin(jwt)) {
      return Mono.just(error(HttpStatus.FORBIDDEN, ErrorCode.ADMIN_ACCESS_DENIED, "Admin access denied", requestId));
    }

    AuditSource auditSource = AuditSource.fromValue(source);
    if (auditSource == null) {
      return Mono.just(error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_AUDIT_SOURCE, "Invalid audit source", requestId));
    }

    Map<String, String> query = new LinkedHashMap<>(allQueryParams);
    query.remove("source");

    Map<String, String> normalized;
    try {
      normalized = AuditProxyService.validateAndNormalize(query);
    } catch (AuditProxyException e) {
      return Mono.just(error(e.status(), e.errorCode(), e.getMessage(), requestId));
    }

    log.info(
        "admin_audit_query adminUserId={} adminEmail={} source={} requestId={} filters={}",
        jwt.getSubject(),
        jwt.getClaimAsString("email"),
        auditSource.value(),
        requestId,
        normalized);

    return auditProxyService
        .proxy(auditSource, normalized)
        .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).<Object>body(body))
        .onErrorResume(
            AuditProxyException.class,
            e -> Mono.just(error(e.status(), e.errorCode(), e.getMessage(), requestId)));
  }

  private ResponseEntity<Object> error(
      HttpStatus status, ErrorCode code, String message, String requestId) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .<Object>body(
            new ErrorResponse(
                Instant.now(), status.value(), code.name(), message, "/admin/audit-events", requestId));
  }
}

package com.notebook.lumen.gateway.admin.enterprise;

import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import com.notebook.lumen.gateway.filter.GatewayHeaders;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class AdminEnterpriseStatusController {
  private static final Logger log = LoggerFactory.getLogger(AdminEnterpriseStatusController.class);

  private final AdminAuthorizationService adminAuthorizationService;
  private final EnterpriseStatusAggregationService aggregationService;

  public AdminEnterpriseStatusController(
      AdminAuthorizationService adminAuthorizationService,
      EnterpriseStatusAggregationService aggregationService) {
    this.adminAuthorizationService = adminAuthorizationService;
    this.aggregationService = aggregationService;
  }

  @GetMapping(path = "/admin/enterprise/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> enterpriseStatus(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    if (!adminAuthorizationService.enterpriseFeatureEnabled()) {
      return Mono.just(
          error(
              HttpStatus.NOT_FOUND,
              ErrorCode.ADMIN_ENTERPRISE_DISABLED,
              "Enterprise admin console is disabled on this gateway.",
              requestId));
    }
    if (!adminAuthorizationService.isAdmin(jwt)) {
      if (adminAuthorizationService.requiresMfa()) {
        log.warn(
            "admin_mfa_required_blocked adminUserId={} endpoint={} requestId={} amr={}",
            jwt == null ? null : jwt.getSubject(),
            "/admin/enterprise/status",
            requestId,
            jwt == null ? null : jwt.getClaims().get("amr"));
        return Mono.just(
            error(
                HttpStatus.FORBIDDEN,
                ErrorCode.ADMIN_MFA_REQUIRED,
                "Admin access requires multi-factor authentication.",
                requestId));
      }
      return Mono.just(
          error(HttpStatus.FORBIDDEN, ErrorCode.ADMIN_ACCESS_DENIED, "Admin access denied", requestId));
    }

    return aggregationService
        .loadStatus()
        .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).<Object>body(body));
  }

  private ResponseEntity<Object> error(
      HttpStatus status, ErrorCode code, String message, String requestId) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse(
                Instant.now(), status.value(), code.name(), message, "/admin/enterprise/status", requestId));
  }
}

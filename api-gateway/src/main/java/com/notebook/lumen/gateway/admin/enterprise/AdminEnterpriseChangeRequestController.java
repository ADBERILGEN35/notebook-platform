package com.notebook.lumen.gateway.admin.enterprise;

import com.notebook.lumen.gateway.admin.AdminAuthorizationService;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import com.notebook.lumen.gateway.filter.GatewayHeaders;
import java.time.Instant;
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
import reactor.core.publisher.Mono;

@RestController
public class AdminEnterpriseChangeRequestController {
  private static final Logger log =
      LoggerFactory.getLogger(AdminEnterpriseChangeRequestController.class);
  static final String PATH_PREFIX = "/admin/enterprise/change-requests";

  private final AdminAuthorizationService adminAuthorizationService;
  private final EnterpriseChangeRequestProxyService proxyService;

  public AdminEnterpriseChangeRequestController(
      AdminAuthorizationService adminAuthorizationService,
      EnterpriseChangeRequestProxyService proxyService) {
    this.adminAuthorizationService = adminAuthorizationService;
    this.proxyService = proxyService;
  }

  @GetMapping(path = PATH_PREFIX, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "status", required = false) String status,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    return authorizeAndProxy(
        jwt,
        requestId,
        PATH_PREFIX,
        () -> proxyService.list(status, jwt.getSubject(), jwt.getClaimAsString("email"), requestId, PATH_PREFIX));
  }

  @PostMapping(
      path = PATH_PREFIX + "/validate",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> validate(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    return authorizeAndProxy(
        jwt,
        requestId,
        PATH_PREFIX + "/validate",
        () ->
            proxyService.validate(
                body, jwt.getSubject(), jwt.getClaimAsString("email"), requestId, PATH_PREFIX + "/validate"));
  }

  @PostMapping(
      path = PATH_PREFIX,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    return authorizeAndProxy(
        jwt,
        requestId,
        PATH_PREFIX,
        () ->
            proxyService.create(
                body, jwt.getSubject(), jwt.getClaimAsString("email"), requestId, PATH_PREFIX));
  }

  @PostMapping(path = PATH_PREFIX + "/{id}/cancel")
  public Mono<ResponseEntity<Object>> cancel(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    return authorizeAndProxy(
        jwt,
        requestId,
        PATH_PREFIX + "/{id}/cancel",
        () ->
            proxyService.cancel(
                id, jwt.getSubject(), jwt.getClaimAsString("email"), requestId, PATH_PREFIX + "/" + id + "/cancel"));
  }

  @PostMapping(
      path = PATH_PREFIX + "/{id}/approve",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Map<String, Object> payload = body == null ? Map.of() : body;
    return authorizeAndProxy(
        jwt,
        requestId,
        PATH_PREFIX + "/{id}/approve",
        () ->
            proxyService.approve(
                id,
                payload,
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                requestId,
                PATH_PREFIX + "/" + id + "/approve"));
  }

  @PostMapping(
      path = PATH_PREFIX + "/{id}/reject",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Object>> reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(name = GatewayHeaders.REQUEST_ID, required = false) String requestId) {
    Map<String, Object> payload = body == null ? Map.of() : body;
    return authorizeAndProxy(
        jwt,
        requestId,
        PATH_PREFIX + "/{id}/reject",
        () ->
            proxyService.reject(
                id,
                payload,
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                requestId,
                PATH_PREFIX + "/" + id + "/reject"));
  }

  private Mono<ResponseEntity<Object>> authorizeAndProxy(
      Jwt jwt, String requestId, String path, java.util.function.Supplier<Mono<ResponseEntity<Object>>> call) {
    if (!adminAuthorizationService.adminWriteFeatureEnabled()) {
      return Mono.just(
          error(
              HttpStatus.NOT_FOUND,
              ErrorCode.ADMIN_WRITE_DISABLED,
              "Enterprise admin write operations are disabled on this gateway.",
              path,
              requestId));
    }
    Optional<ErrorCode> denial = adminAuthorizationService.enterpriseAdminWriteDenialReason(jwt);
    if (denial.isPresent()) {
      ErrorCode code = denial.get();
      if (code == ErrorCode.ADMIN_WRITE_MFA_REQUIRED) {
        log.warn(
            "admin_write_mfa_required adminUserId={} path={} requestId={}",
            jwt == null ? null : jwt.getSubject(),
            path,
            requestId);
      }
      String message =
          code == ErrorCode.ADMIN_WRITE_MFA_REQUIRED
              ? "Admin write requires multi-factor authentication."
              : "Admin write access denied.";
      return Mono.just(error(HttpStatus.FORBIDDEN, code, message, path, requestId));
    }
    return call.get();
  }

  private ResponseEntity<Object> error(
      HttpStatus status, ErrorCode code, String message, String path, String requestId) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse(Instant.now(), status.value(), code.name(), message, path, requestId));
  }
}

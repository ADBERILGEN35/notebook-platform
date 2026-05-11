package com.notebook.lumen.gateway.admin.rbac;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayAdminRbacVisibilityProperties;
import com.notebook.lumen.gateway.config.GatewayAuditProxyProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Service
public class AdminRbacProxyService {
  static final String RBAC_READ_SCOPE = "internal:admin:rbac:read";
  private static final String IDENTITY_AUDIENCE = "identity-service";
  private static final String HDR_ADMIN_USER_ID = "X-Admin-User-Id";

  private final GatewayAuditProxyProperties auditProxyProperties;
  private final GatewayAdminRbacVisibilityProperties rbacVisibilityProperties;
  private final ServiceJwtSigner serviceJwtSigner;
  private final WebClient webClient;

  public AdminRbacProxyService(
      GatewayAuditProxyProperties auditProxyProperties,
      GatewayAdminRbacVisibilityProperties rbacVisibilityProperties,
      ServiceJwtSigner auditServiceJwtSigner,
      WebClient.Builder webClientBuilder) {
    this.auditProxyProperties = auditProxyProperties;
    this.rbacVisibilityProperties = rbacVisibilityProperties;
    this.serviceJwtSigner = auditServiceJwtSigner;
    this.webClient = webClientBuilder.build();
  }

  public Mono<ResponseEntity<Object>> listUsers(
      String q,
      String role,
      String permission,
      int page,
      int size,
      String adminUserId,
      String requestId,
      String gatewayPath) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, RBAC_READ_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, gatewayPath));
          }
          UriComponentsBuilder ub =
              UriComponentsBuilder.fromUriString(baseUrl() + "/users")
                  .queryParam("page", page)
                  .queryParam("size", size);
          if (q != null && !q.isBlank()) {
            ub.queryParam("q", q.trim());
          }
          if (role != null && !role.isBlank()) {
            ub.queryParam("role", role.trim());
          }
          if (permission != null && !permission.isBlank()) {
            ub.queryParam("permission", permission.trim());
          }
          String url = ub.build(true).toUriString();
          WebClient.RequestHeadersSpec<?> spec =
              webClient
                  .get()
                  .uri(url)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
                  .header(HDR_ADMIN_USER_ID, adminUserId);
          if (requestId != null && !requestId.isBlank()) {
            spec = spec.header("X-Request-Id", requestId);
          }
          return spec
              .retrieve()
              .bodyToMono(Object.class)
              .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, requestId)));
        });
  }

  public Mono<ResponseEntity<Object>> overridesStatus(String adminUserId, String requestId, String gatewayPath) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, RBAC_READ_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, gatewayPath));
          }
          String url = baseUrl() + "/overrides/status";
          WebClient.RequestHeadersSpec<?> spec =
              webClient
                  .get()
                  .uri(url)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
                  .header(HDR_ADMIN_USER_ID, adminUserId);
          if (requestId != null && !requestId.isBlank()) {
            spec = spec.header("X-Request-Id", requestId);
          }
          return spec
              .retrieve()
              .bodyToMono(Object.class)
              .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, requestId)));
        });
  }

  public Mono<ResponseEntity<Object>> overridesValidate(
      Map<String, Object> body, String adminUserId, String requestId, String gatewayPath) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, RBAC_READ_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, gatewayPath));
          }
          String url = baseUrl() + "/overrides/validate";
          WebClient.RequestBodySpec spec =
              webClient
                  .post()
                  .uri(url)
                  .accept(MediaType.APPLICATION_JSON)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
                  .header(HDR_ADMIN_USER_ID, adminUserId);
          if (requestId != null && !requestId.isBlank()) {
            spec = spec.header("X-Request-Id", requestId);
          }
          return spec
              .bodyValue(body == null ? Map.of() : body)
              .retrieve()
              .bodyToMono(Object.class)
              .map(resp -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, requestId)));
        });
  }

  public Mono<ResponseEntity<Object>> userDetail(
      java.util.UUID userId, String adminUserId, String requestId, String gatewayPath) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, RBAC_READ_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, gatewayPath));
          }
          String url = baseUrl() + "/users/" + userId;
          WebClient.RequestHeadersSpec<?> spec =
              webClient
                  .get()
                  .uri(url)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
                  .header(HDR_ADMIN_USER_ID, adminUserId);
          if (requestId != null && !requestId.isBlank()) {
            spec = spec.header("X-Request-Id", requestId);
          }
          return spec
              .retrieve()
              .bodyToMono(Object.class)
              .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, requestId)));
        });
  }

  private static ResponseEntity<Object> jwtFailure(String requestId, String gatewayPath) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new com.notebook.lumen.gateway.error.ErrorResponse(
                java.time.Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ErrorCode.AUDIT_PROXY_INTERNAL_AUTH_FAILED.name(),
                "Service JWT signing failed for RBAC proxy",
                gatewayPath,
                requestId,
                null));
  }

  private static ResponseEntity<Object> mapException(Throwable e, String gatewayPath, String requestId) {
    if (e instanceof WebClientResponseException w) {
      return ResponseEntity.status(w.getStatusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(w.getResponseBodyAsString());
    }
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new com.notebook.lumen.gateway.error.ErrorResponse(
                java.time.Instant.now(),
                HttpStatus.BAD_GATEWAY.value(),
                ErrorCode.INTERNAL_GATEWAY_ERROR.name(),
                "identity-service unavailable",
                gatewayPath,
                requestId,
                null));
  }

  private String baseUrl() {
    String base = auditProxyProperties.identityServiceUrl();
    if (base == null || base.isBlank()) {
      base = "http://identity-service:8081";
    }
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    String path = rbacVisibilityProperties.effectiveInternalPath();
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    return base + path;
  }
}

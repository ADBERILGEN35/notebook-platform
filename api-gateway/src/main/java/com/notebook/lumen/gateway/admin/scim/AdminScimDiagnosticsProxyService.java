package com.notebook.lumen.gateway.admin.scim;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayAuditProxyProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import java.time.Instant;
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
public class AdminScimDiagnosticsProxyService {
  static final String SCIM_DIAGNOSTICS_SCOPE = "internal:admin:scim:diagnostics:read";
  private static final String IDENTITY_AUDIENCE = "identity-service";
  private static final String INTERNAL_BASE_PATH = "/internal/admin/scim";

  private final GatewayAuditProxyProperties auditProxyProperties;
  private final ServiceJwtSigner serviceJwtSigner;
  private final WebClient webClient;

  public AdminScimDiagnosticsProxyService(
      GatewayAuditProxyProperties auditProxyProperties,
      ServiceJwtSigner auditServiceJwtSigner,
      WebClient.Builder webClientBuilder) {
    this.auditProxyProperties = auditProxyProperties;
    this.serviceJwtSigner = auditServiceJwtSigner;
    this.webClient = webClientBuilder.build();
  }

  public Mono<ResponseEntity<Object>> compatibilityStatus(String requestId, String gatewayPath) {
    return get(baseUrl() + "/compatibility/status", requestId, gatewayPath);
  }

  public Mono<ResponseEntity<Object>> syncCheckpoints(String requestId, String gatewayPath) {
    return get(baseUrl() + "/sync-checkpoints", requestId, gatewayPath);
  }

  public Mono<ResponseEntity<Object>> deltaReadiness(String requestId, String gatewayPath) {
    return get(baseUrl() + "/delta/readiness", requestId, gatewayPath);
  }

  public Mono<ResponseEntity<Object>> deltaDryRun(
      Object body, String requestId, String gatewayPath) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, SCIM_DIAGNOSTICS_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, gatewayPath));
          }
          WebClient.RequestBodySpec spec =
              webClient
                  .post()
                  .uri(baseUrl() + "/delta/dry-run")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt);
          if (requestId != null && !requestId.isBlank()) {
            spec = spec.header("X-Request-Id", requestId);
          }
          return spec.bodyValue(body == null ? Map.of() : body)
              .retrieve()
              .bodyToMono(Object.class)
              .map(
                  response ->
                      ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, requestId)));
        });
  }

  public Mono<ResponseEntity<Object>> syncRuns(
      String provider,
      String resourceType,
      String status,
      String from,
      String to,
      int page,
      int size,
      String requestId,
      String gatewayPath) {
    UriComponentsBuilder ub =
        UriComponentsBuilder.fromUriString(baseUrl() + "/sync-runs")
            .queryParam("page", page)
            .queryParam("size", size);
    if (provider != null && !provider.isBlank()) ub.queryParam("provider", provider.trim());
    if (resourceType != null && !resourceType.isBlank())
      ub.queryParam("resourceType", resourceType.trim());
    if (status != null && !status.isBlank()) ub.queryParam("status", status.trim());
    if (from != null && !from.isBlank()) ub.queryParam("from", from.trim());
    if (to != null && !to.isBlank()) ub.queryParam("to", to.trim());
    return get(ub.build(true).toUriString(), requestId, gatewayPath);
  }

  private Mono<ResponseEntity<Object>> get(String url, String requestId, String gatewayPath) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, SCIM_DIAGNOSTICS_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, gatewayPath));
          }
          WebClient.RequestHeadersSpec<?> spec =
              webClient
                  .get()
                  .uri(url)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt);
          if (requestId != null && !requestId.isBlank()) {
            spec = spec.header("X-Request-Id", requestId);
          }
          return spec.retrieve()
              .bodyToMono(Object.class)
              .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, requestId)));
        });
  }

  private ResponseEntity<Object> jwtFailure(String requestId, String gatewayPath) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new com.notebook.lumen.gateway.error.ErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ErrorCode.AUDIT_PROXY_INTERNAL_AUTH_FAILED.name(),
                "Service JWT signing failed for SCIM diagnostics proxy",
                gatewayPath,
                requestId,
                null));
  }

  private static ResponseEntity<Object> mapException(
      Throwable e, String gatewayPath, String requestId) {
    if (e instanceof WebClientResponseException w) {
      return ResponseEntity.status(w.getStatusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(w.getResponseBodyAsString());
    }
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new com.notebook.lumen.gateway.error.ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_GATEWAY.value(),
                ErrorCode.INTERNAL_GATEWAY_ERROR.name(),
                "identity-service unavailable",
                gatewayPath,
                requestId,
                null));
  }

  private String baseUrl() {
    String base = auditProxyProperties.identityServiceUrl();
    if (base == null || base.isBlank()) base = "http://identity-service:8081";
    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    return base + INTERNAL_BASE_PATH;
  }
}

package com.notebook.lumen.gateway.admin.retention;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayAuditProxyProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
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
public class AdminPlatformRetentionProxyService {
  static final String READ_SCOPE = "internal:admin:retention:platform:read";
  static final String WRITE_SCOPE = "internal:admin:retention:legal-hold:write";
  static final String ACTOR_HEADER = "X-Admin-Actor-User-Id";
  private static final String IDENTITY_AUDIENCE = "identity-service";
  private static final String BASE_PATH = "/internal/admin/retention/platform";

  private final GatewayAuditProxyProperties auditProxyProperties;
  private final ServiceJwtSigner serviceJwtSigner;
  private final WebClient webClient;

  public AdminPlatformRetentionProxyService(
      GatewayAuditProxyProperties auditProxyProperties,
      ServiceJwtSigner serviceJwtSigner,
      WebClient.Builder webClientBuilder) {
    this.auditProxyProperties = auditProxyProperties;
    this.serviceJwtSigner = serviceJwtSigner;
    this.webClient = webClientBuilder.build();
  }

  public Mono<ResponseEntity<Object>> targets(String requestId, String path) {
    return get(baseUrl() + "/targets", READ_SCOPE, requestId, path);
  }

  public Mono<ResponseEntity<Object>> plan(
      String target, boolean dryRun, String requestId, String path) {
    UriComponentsBuilder ub =
        UriComponentsBuilder.fromUriString(baseUrl() + "/plan").queryParam("dryRun", dryRun);
    if (target != null && !target.isBlank()) {
      ub.queryParam("target", target.trim());
    }
    return get(ub.build(true).toUriString(), READ_SCOPE, requestId, path);
  }

  public Mono<ResponseEntity<Object>> legalHolds(String status, String requestId, String path) {
    UriComponentsBuilder ub = UriComponentsBuilder.fromUriString(baseUrl() + "/legal-holds");
    if (status != null && !status.isBlank()) {
      ub.queryParam("status", status.trim());
    }
    return get(ub.build(true).toUriString(), READ_SCOPE, requestId, path);
  }

  public Mono<ResponseEntity<Object>> createLegalHold(
      Map<String, Object> body, String actorUserId, String requestId, String path) {
    return post(baseUrl() + "/legal-holds", body, actorUserId, requestId, path);
  }

  public Mono<ResponseEntity<Object>> releaseLegalHold(
      String id, Map<String, Object> body, String actorUserId, String requestId, String path) {
    return post(baseUrl() + "/legal-holds/" + id + "/release", body, actorUserId, requestId, path);
  }

  private Mono<ResponseEntity<Object>> get(
      String url, String scope, String requestId, String gatewayPath) {
    return Mono.defer(
        () -> {
          String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, scope);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, gatewayPath));
          }
          WebClient.RequestHeadersSpec<?> spec =
              webClient
                  .get()
                  .uri(url)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt);
          if (requestId != null && !requestId.isBlank()) spec = spec.header("X-Request-Id", requestId);
          return spec.retrieve()
              .bodyToMono(Object.class)
              .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, requestId)));
        });
  }

  private Mono<ResponseEntity<Object>> post(
      String url,
      Map<String, Object> body,
      String actorUserId,
      String requestId,
      String gatewayPath) {
    return Mono.defer(
        () -> {
          String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, WRITE_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, gatewayPath));
          }
          WebClient.RequestBodySpec spec =
              webClient
                  .post()
                  .uri(url)
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
                  .header(ACTOR_HEADER, actorUserId);
          if (requestId != null && !requestId.isBlank()) spec = spec.header("X-Request-Id", requestId);
          return spec.bodyValue(body == null ? Map.of() : body)
              .retrieve()
              .bodyToMono(Object.class)
              .map(response -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, requestId)));
        });
  }

  private ResponseEntity<Object> jwtFailure(String requestId, String gatewayPath) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ErrorCode.AUDIT_PROXY_INTERNAL_AUTH_FAILED.name(),
                "Service JWT signing failed for platform retention proxy",
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
            new ErrorResponse(
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
    return base + BASE_PATH;
  }
}

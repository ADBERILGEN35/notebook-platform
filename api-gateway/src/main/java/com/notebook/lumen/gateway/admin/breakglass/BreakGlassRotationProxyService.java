package com.notebook.lumen.gateway.admin.breakglass;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayAuditProxyProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Service
public class BreakGlassRotationProxyService {
  static final String READ_SCOPE = "internal:admin:break-glass:rotation:read";
  static final String MANAGE_SCOPE = "internal:admin:break-glass:rotation:manage";
  private static final String IDENTITY_AUDIENCE = "identity-service";
  private static final String HDR_ADMIN_USER_ID = "X-Admin-User-Id";

  private final GatewayAuditProxyProperties auditProxyProperties;
  private final ServiceJwtSigner signer;
  private final WebClient webClient;

  public BreakGlassRotationProxyService(
      GatewayAuditProxyProperties auditProxyProperties,
      ServiceJwtSigner signer,
      WebClient.Builder webClientBuilder) {
    this.auditProxyProperties = auditProxyProperties;
    this.signer = signer;
    this.webClient = webClientBuilder.build();
  }

  public Mono<ResponseEntity<Object>> list(
      String status, int page, int size, String adminUserId, String requestId, String gatewayPath) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = signer.sign(IDENTITY_AUDIENCE, READ_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, gatewayPath));
          }
          UriComponentsBuilder ub =
              UriComponentsBuilder.fromUriString(baseUrl())
                  .queryParam("page", page)
                  .queryParam("size", size);
          if (status != null && !status.isBlank()) ub.queryParam("status", status);
          WebClient.RequestHeadersSpec<?> spec =
              webClient
                  .get()
                  .uri(ub.build(true).toUriString())
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
                  .header(HDR_ADMIN_USER_ID, adminUserId);
          if (requestId != null && !requestId.isBlank())
            spec = spec.header("X-Request-Id", requestId);
          return spec.retrieve()
              .bodyToMono(Object.class)
              .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, requestId)));
        });
  }

  public Mono<ResponseEntity<Object>> detail(
      UUID id, String adminUserId, String requestId, String path) {
    return withAuthGet(baseUrl() + "/" + id, READ_SCOPE, adminUserId, requestId, path);
  }

  public Mono<ResponseEntity<Object>> acknowledge(
      UUID id, Map<String, Object> body, String adminUserId, String requestId, String path) {
    return postWithBody(baseUrl() + "/" + id + "/acknowledge", body, adminUserId, requestId, path);
  }

  public Mono<ResponseEntity<Object>> verify(
      UUID id, Map<String, Object> body, String adminUserId, String requestId, String path) {
    return postWithBody(baseUrl() + "/" + id + "/verify", body, adminUserId, requestId, path);
  }

  public Mono<ResponseEntity<Object>> close(
      UUID id, Map<String, Object> body, String adminUserId, String requestId, String path) {
    return postWithBody(baseUrl() + "/" + id + "/close", body, adminUserId, requestId, path);
  }

  private Mono<ResponseEntity<Object>> postWithBody(
      String url, Map<String, Object> body, String adminUserId, String requestId, String path) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = signer.sign(IDENTITY_AUDIENCE, MANAGE_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, path));
          }
          WebClient.RequestBodySpec spec =
              webClient
                  .post()
                  .uri(url)
                  .accept(MediaType.APPLICATION_JSON)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
                  .header(HDR_ADMIN_USER_ID, adminUserId);
          if (requestId != null && !requestId.isBlank())
            spec = spec.header("X-Request-Id", requestId);
          return spec.bodyValue(body == null ? Map.of() : body)
              .retrieve()
              .bodyToMono(Object.class)
              .map(resp -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp))
              .onErrorResume(e -> Mono.just(mapException(e, path, requestId)));
        });
  }

  private Mono<ResponseEntity<Object>> withAuthGet(
      String url, String scope, String adminUserId, String requestId, String path) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = signer.sign(IDENTITY_AUDIENCE, scope);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, path));
          }
          WebClient.RequestHeadersSpec<?> spec =
              webClient
                  .get()
                  .uri(url)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
                  .header(HDR_ADMIN_USER_ID, adminUserId);
          if (requestId != null && !requestId.isBlank())
            spec = spec.header("X-Request-Id", requestId);
          return spec.retrieve()
              .bodyToMono(Object.class)
              .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body))
              .onErrorResume(e -> Mono.just(mapException(e, path, requestId)));
        });
  }

  private static ResponseEntity<Object> mapException(Throwable e, String path, String requestId) {
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
                path,
                requestId,
                null));
  }

  private static ResponseEntity<Object> jwtFailure(String requestId, String path) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ErrorCode.AUDIT_PROXY_INTERNAL_AUTH_FAILED.name(),
                "Service JWT signing failed for break-glass rotation proxy",
                path,
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
    return base + "/internal/admin/break-glass/rotation-events";
  }
}

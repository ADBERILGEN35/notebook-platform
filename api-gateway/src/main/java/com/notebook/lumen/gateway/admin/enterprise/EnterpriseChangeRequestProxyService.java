package com.notebook.lumen.gateway.admin.enterprise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayAdminWriteProperties;
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
public class EnterpriseChangeRequestProxyService {
  static final String CHANGE_REQUEST_SCOPE = "internal:admin:change-requests:manage";
  private static final String IDENTITY_AUDIENCE = "identity-service";
  private static final String HDR_ADMIN_USER_ID = "X-Admin-User-Id";
  private static final String HDR_ADMIN_USER_EMAIL = "X-Admin-User-Email";

  private final GatewayAuditProxyProperties auditProxyProperties;
  private final GatewayAdminWriteProperties writeProperties;
  private final ServiceJwtSigner serviceJwtSigner;
  private final WebClient webClient;
  private final ObjectMapper objectMapper;

  public EnterpriseChangeRequestProxyService(
      GatewayAuditProxyProperties auditProxyProperties,
      GatewayAdminWriteProperties writeProperties,
      ServiceJwtSigner auditServiceJwtSigner,
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper) {
    this.auditProxyProperties = auditProxyProperties;
    this.writeProperties = writeProperties;
    this.serviceJwtSigner = auditServiceJwtSigner;
    this.webClient = webClientBuilder.build();
    this.objectMapper = objectMapper;
  }

  public Mono<ResponseEntity<Object>> list(
      String statusFilter,
      String adminUserId,
      String adminEmail,
      String clientRequestId,
      String gatewayPath) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, CHANGE_REQUEST_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailureResponse(gatewayPath, clientRequestId));
          }
          UriComponentsBuilder ub = UriComponentsBuilder.fromUriString(changeRequestsBaseUrl());
          if (statusFilter != null && !statusFilter.isBlank()) {
            ub.queryParam("status", statusFilter.trim());
          }
          String url = ub.build(true).toUriString();
          WebClient.RequestHeadersSpec<?> spec =
              webClient
                  .get()
                  .uri(url)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
                  .header(HDR_ADMIN_USER_ID, adminUserId);
          if (adminEmail != null && !adminEmail.isBlank()) {
            spec = spec.header(HDR_ADMIN_USER_EMAIL, adminEmail);
          }
          if (clientRequestId != null && !clientRequestId.isBlank()) {
            spec = spec.header("X-Request-Id", clientRequestId);
          }
          return spec.retrieve()
              .bodyToMono(String.class)
              .map(raw -> jsonEntity(HttpStatus.OK, raw, gatewayPath, clientRequestId))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, clientRequestId)));
        });
  }

  public Mono<ResponseEntity<Object>> validate(
      Map<String, Object> body,
      String adminUserId,
      String adminEmail,
      String clientRequestId,
      String gatewayPath) {
    return postJson("/validate", body, adminUserId, adminEmail, clientRequestId, gatewayPath);
  }

  public Mono<ResponseEntity<Object>> create(
      Map<String, Object> body,
      String adminUserId,
      String adminEmail,
      String clientRequestId,
      String gatewayPath) {
    return postJson("", body, adminUserId, adminEmail, clientRequestId, gatewayPath);
  }

  public Mono<ResponseEntity<Object>> approve(
      UUID id,
      Map<String, Object> body,
      String adminUserId,
      String adminEmail,
      String clientRequestId,
      String gatewayPath) {
    return postJson(
        "/" + id + "/approve", body, adminUserId, adminEmail, clientRequestId, gatewayPath);
  }

  public Mono<ResponseEntity<Object>> reject(
      UUID id,
      Map<String, Object> body,
      String adminUserId,
      String adminEmail,
      String clientRequestId,
      String gatewayPath) {
    return postJson(
        "/" + id + "/reject", body, adminUserId, adminEmail, clientRequestId, gatewayPath);
  }

  public Mono<ResponseEntity<Object>> gitopsDryRun(
      UUID id,
      Map<String, Object> body,
      String adminUserId,
      String adminEmail,
      String clientRequestId,
      String gatewayPath) {
    return postJson(
        "/" + id + "/gitops/dry-run", body, adminUserId, adminEmail, clientRequestId, gatewayPath);
  }

  public Mono<ResponseEntity<Object>> gitopsCreatePr(
      UUID id,
      Map<String, Object> body,
      String adminUserId,
      String adminEmail,
      String clientRequestId,
      String gatewayPath) {
    return postJson(
        "/" + id + "/gitops/create-pr",
        body,
        adminUserId,
        adminEmail,
        clientRequestId,
        gatewayPath);
  }

  public Mono<ResponseEntity<Object>> cancel(
      UUID id,
      String adminUserId,
      String adminEmail,
      String clientRequestId,
      String gatewayPath,
      boolean globalCancel) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, CHANGE_REQUEST_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailureResponse(gatewayPath, clientRequestId));
          }
          String url =
              UriComponentsBuilder.fromUriString(changeRequestsBaseUrl() + "/" + id + "/cancel")
                  .queryParam("globalCancel", globalCancel)
                  .build(true)
                  .toUriString();
          WebClient.RequestBodySpec spec =
              webClient
                  .post()
                  .uri(url)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
                  .header(HDR_ADMIN_USER_ID, adminUserId);
          if (adminEmail != null && !adminEmail.isBlank()) {
            spec = spec.header(HDR_ADMIN_USER_EMAIL, adminEmail);
          }
          if (clientRequestId != null && !clientRequestId.isBlank()) {
            spec = spec.header("X-Request-Id", clientRequestId);
          }
          return spec.exchangeToMono(
                  response -> {
                    if (response.statusCode().value() == 204) {
                      return Mono.just(ResponseEntity.noContent().build());
                    }
                    return response
                        .bodyToMono(String.class)
                        .defaultIfEmpty("{}")
                        .map(
                            raw ->
                                rawEntity(
                                    response.statusCode().value(),
                                    raw,
                                    gatewayPath,
                                    clientRequestId));
                  })
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, clientRequestId)));
        });
  }

  private Mono<ResponseEntity<Object>> postJson(
      String suffix,
      Map<String, Object> body,
      String adminUserId,
      String adminEmail,
      String clientRequestId,
      String gatewayPath) {
    return Mono.defer(
        () -> {
          final String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, CHANGE_REQUEST_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailureResponse(gatewayPath, clientRequestId));
          }
          String path = writeProperties.effectiveInternalPath() + suffix;
          String url = trimTrailingSlash(auditProxyProperties.identityServiceUrl()) + path;
          return webClient
              .post()
              .uri(url)
              .contentType(MediaType.APPLICATION_JSON)
              .bodyValue(body)
              .headers(
                  h -> {
                    h.set(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt);
                    h.set(HDR_ADMIN_USER_ID, adminUserId);
                    if (adminEmail != null && !adminEmail.isBlank()) {
                      h.set(HDR_ADMIN_USER_EMAIL, adminEmail);
                    }
                    if (clientRequestId != null && !clientRequestId.isBlank()) {
                      h.set("X-Request-Id", clientRequestId);
                    }
                  })
              .exchangeToMono(
                  response -> {
                    int code = response.statusCode().value();
                    if (code == 204) {
                      return Mono.just(ResponseEntity.noContent().build());
                    }
                    return response
                        .bodyToMono(String.class)
                        .defaultIfEmpty("{}")
                        .map(raw -> rawEntity(code, raw, gatewayPath, clientRequestId));
                  })
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, clientRequestId)));
        });
  }

  private ResponseEntity<Object> jwtFailureResponse(String gatewayPath, String clientRequestId) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            new ErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ErrorCode.AUDIT_PROXY_INTERNAL_AUTH_FAILED.name(),
                "Service JWT signing failed for change-request proxy",
                gatewayPath,
                clientRequestId));
  }

  private String changeRequestsBaseUrl() {
    return trimTrailingSlash(auditProxyProperties.identityServiceUrl())
        + writeProperties.effectiveInternalPath();
  }

  private ResponseEntity<Object> jsonEntity(
      HttpStatus status, String raw, String gatewayPath, String clientRequestId) {
    return rawEntity(status.value(), raw, gatewayPath, clientRequestId);
  }

  private ResponseEntity<Object> rawEntity(
      int status, String raw, String gatewayPath, String clientRequestId) {
    try {
      if (status >= 400) {
        return ResponseEntity.status(status)
            .body(parseOrGenericError(raw, status, gatewayPath, clientRequestId));
      }
      Object parsed =
          objectMapper.readValue(raw == null || raw.isBlank() ? "{}" : raw, Object.class);
      return ResponseEntity.status(status).body(parsed);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(
              new ErrorResponse(
                  Instant.now(),
                  HttpStatus.BAD_GATEWAY.value(),
                  ErrorCode.INTERNAL_GATEWAY_ERROR.name(),
                  "Invalid response from identity-service",
                  gatewayPath,
                  clientRequestId));
    }
  }

  private Object parseOrGenericError(
      String raw, int status, String gatewayPath, String clientRequestId) {
    try {
      var node = objectMapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
      String code = node.path("errorCode").asText(null);
      String message = node.path("message").asText("Request failed");
      if (code != null && !code.isBlank()) {
        return new ErrorResponse(
            Instant.now(), status, code, message, gatewayPath, clientRequestId);
      }
    } catch (Exception ignored) {
    }
    return new ErrorResponse(
        Instant.now(),
        status,
        ErrorCode.INTERNAL_GATEWAY_ERROR.name(),
        "Downstream request failed",
        gatewayPath,
        clientRequestId);
  }

  private ResponseEntity<Object> mapException(
      Throwable e, String gatewayPath, String clientRequestId) {
    if (e instanceof WebClientResponseException w) {
      return rawEntity(
          w.getStatusCode().value(), w.getResponseBodyAsString(), gatewayPath, clientRequestId);
    }
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(
            new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_GATEWAY.value(),
                ErrorCode.INTERNAL_GATEWAY_ERROR.name(),
                "identity-service unavailable",
                gatewayPath,
                clientRequestId));
  }

  private static String trimTrailingSlash(String base) {
    if (base == null || base.isBlank()) {
      return "";
    }
    String s = base.trim();
    while (s.endsWith("/")) {
      s = s.substring(0, s.length() - 1);
    }
    return s;
  }
}

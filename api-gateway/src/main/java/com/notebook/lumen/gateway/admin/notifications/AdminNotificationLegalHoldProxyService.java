package com.notebook.lumen.gateway.admin.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayAdminEnterpriseProperties;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
public class AdminNotificationLegalHoldProxyService {
  private static final Logger log = LoggerFactory.getLogger(AdminNotificationLegalHoldProxyService.class);
  static final String LEGAL_HOLD_READ_SCOPE = "internal:admin:notifications:legal-hold:read";
  static final String LEGAL_HOLD_WRITE_SCOPE = "internal:admin:notifications:legal-hold:write";
  private static final String NOTIFICATION_AUDIENCE = "notification-service";

  private final WebClient webClient;
  private final ServiceJwtSigner serviceJwtSigner;
  private final GatewayAdminEnterpriseProperties enterpriseProperties;
  private final ObjectMapper objectMapper;

  public AdminNotificationLegalHoldProxyService(
      WebClient.Builder webClientBuilder,
      ServiceJwtSigner auditServiceJwtSigner,
      GatewayAdminEnterpriseProperties enterpriseProperties,
      ObjectMapper objectMapper) {
    this.webClient = webClientBuilder.build();
    this.serviceJwtSigner = auditServiceJwtSigner;
    this.enterpriseProperties = enterpriseProperties;
    this.objectMapper = objectMapper;
  }

  public Mono<JsonNode> list(String queryString) {
    String jwt = serviceJwtSigner.sign(NOTIFICATION_AUDIENCE, LEGAL_HOLD_READ_SCOPE);
    String base =
        trimTrailingSlash(enterpriseProperties.effectiveNotificationServiceUrl())
            + enterpriseProperties.effectiveNotificationLegalHoldPath();
    String uri = base + (queryString == null || queryString.isBlank() ? "" : "?" + queryString);
    return getJson(uri, jwt);
  }

  public Mono<JsonNode> create(Map<String, Object> body, String actorUserId, String actorEmail) {
    String serviceJwt = serviceJwtSigner.sign(NOTIFICATION_AUDIENCE, LEGAL_HOLD_WRITE_SCOPE);
    return postJson(body, actorUserId, actorEmail, "", serviceJwt);
  }

  public Mono<JsonNode> release(String id, Map<String, Object> body, String actorUserId) {
    String serviceJwt = serviceJwtSigner.sign(NOTIFICATION_AUDIENCE, LEGAL_HOLD_WRITE_SCOPE);
    return postJson(body, actorUserId, null, "/" + id + "/release", serviceJwt);
  }

  private Mono<JsonNode> postJson(
      Map<String, Object> body,
      String actorUserId,
      String actorEmail,
      String pathSuffix,
      String serviceJwt) {
    String uri =
        trimTrailingSlash(enterpriseProperties.effectiveNotificationServiceUrl())
            + enterpriseProperties.effectiveNotificationLegalHoldPath()
            + pathSuffix;
    try {
      String json = objectMapper.writeValueAsString(body);
      var req =
          webClient
              .post()
              .uri(uri)
              .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + serviceJwt)
              .header(AdminNotificationRetentionProxyService.ADMIN_ACTOR_HEADER, actorUserId)
              .contentType(MediaType.APPLICATION_JSON)
              .bodyValue(json);
      if (actorEmail != null && !actorEmail.isBlank()) {
        req = req.header("X-Admin-Actor-Email", actorEmail);
      }
      return req
          .retrieve()
          .bodyToMono(String.class)
          .timeout(Duration.ofSeconds(30))
          .map(this::parseJson)
          .onErrorResume(
              e -> {
                log.warn(
                    "notification_legal_hold_proxy_failed message={}",
                    e instanceof WebClientResponseException w
                        ? w.getStatusCode().value() + " " + w.getResponseBodyAsString()
                        : e.getMessage());
                return Mono.error(e);
              });
    } catch (Exception e) {
      return Mono.error(e);
    }
  }

  private Mono<JsonNode> getJson(String uri, String jwt) {
    return webClient
        .get()
        .uri(uri)
        .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(30))
        .map(this::parseJson)
        .onErrorResume(
            e -> {
              log.warn(
                  "notification_legal_hold_list_proxy_failed message={}",
                  e instanceof WebClientResponseException w
                      ? w.getStatusCode().value() + " " + w.getResponseBodyAsString()
                      : e.getMessage());
              return Mono.error(e);
            });
  }

  private JsonNode parseJson(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (Exception e) {
      throw new IllegalStateException("invalid_json", e);
    }
  }

  private static String trimTrailingSlash(String base) {
    if (base.endsWith("/")) {
      return base.substring(0, base.length() - 1);
    }
    return base;
  }
}

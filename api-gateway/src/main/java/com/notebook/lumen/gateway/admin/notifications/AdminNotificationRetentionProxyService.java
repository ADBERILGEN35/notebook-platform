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
public class AdminNotificationRetentionProxyService {
  private static final Logger log = LoggerFactory.getLogger(AdminNotificationRetentionProxyService.class);
  static final String RETENTION_READ_SCOPE = "internal:admin:notifications:retention:read";
  static final String RETENTION_RUN_SCOPE = "internal:admin:notifications:retention:run";
  private static final String NOTIFICATION_AUDIENCE = "notification-service";
  public static final String ADMIN_ACTOR_HEADER = "X-Admin-Actor-User-Id";

  private final WebClient webClient;
  private final ServiceJwtSigner serviceJwtSigner;
  private final GatewayAdminEnterpriseProperties enterpriseProperties;
  private final ObjectMapper objectMapper;

  public AdminNotificationRetentionProxyService(
      WebClient.Builder webClientBuilder,
      ServiceJwtSigner auditServiceJwtSigner,
      GatewayAdminEnterpriseProperties enterpriseProperties,
      ObjectMapper objectMapper) {
    this.webClient = webClientBuilder.build();
    this.serviceJwtSigner = auditServiceJwtSigner;
    this.enterpriseProperties = enterpriseProperties;
    this.objectMapper = objectMapper;
  }

  public Mono<JsonNode> plan(boolean dryRun) {
    String jwt = serviceJwtSigner.sign(NOTIFICATION_AUDIENCE, RETENTION_READ_SCOPE);
    String base =
        trimTrailingSlash(enterpriseProperties.effectiveNotificationServiceUrl())
            + enterpriseProperties.effectiveNotificationRetentionPath();
    String uri = base + "/plan?dryRun=" + dryRun;
    return getJson(uri, jwt);
  }

  public Mono<JsonNode> run(Map<String, Object> body, boolean destructive, String actorUserId) {
    String scope = destructive ? RETENTION_RUN_SCOPE : RETENTION_READ_SCOPE;
    String jwt = serviceJwtSigner.sign(NOTIFICATION_AUDIENCE, scope);
    String uri =
        trimTrailingSlash(enterpriseProperties.effectiveNotificationServiceUrl())
            + enterpriseProperties.effectiveNotificationRetentionPath()
            + "/run";
    try {
      String json = objectMapper.writeValueAsString(body);
      var req =
          webClient
              .post()
              .uri(uri)
              .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
              .contentType(MediaType.APPLICATION_JSON)
              .bodyValue(json);
      if (destructive && actorUserId != null && !actorUserId.isBlank()) {
        req = req.header(ADMIN_ACTOR_HEADER, actorUserId);
      }
      return req
          .retrieve()
          .bodyToMono(String.class)
          .timeout(Duration.ofSeconds(30))
          .map(this::parseJson)
          .onErrorResume(
              e -> {
                log.warn(
                    "notification_retention_run_proxy_failed message={}",
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
                  "notification_retention_plan_proxy_failed message={}",
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

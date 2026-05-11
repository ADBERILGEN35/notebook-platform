package com.notebook.lumen.gateway.admin.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayAdminEnterpriseProperties;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
public class AdminNotificationDeadLetterProxyService {
  private static final Logger log =
      LoggerFactory.getLogger(AdminNotificationDeadLetterProxyService.class);
  static final String DEAD_LETTER_READ_SCOPE = "internal:admin:notifications:dead-letter:read";
  static final String DEAD_LETTER_REQUEUE_SCOPE =
      "internal:admin:notifications:dead-letter:requeue";
  private static final String NOTIFICATION_AUDIENCE = "notification-service";
  public static final String ADMIN_ACTOR_HEADER = "X-Admin-Actor-User-Id";

  private final WebClient webClient;
  private final ServiceJwtSigner serviceJwtSigner;
  private final GatewayAdminEnterpriseProperties enterpriseProperties;
  private final ObjectMapper objectMapper;

  public AdminNotificationDeadLetterProxyService(
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
    String jwt = serviceJwtSigner.sign(NOTIFICATION_AUDIENCE, DEAD_LETTER_READ_SCOPE);
    String uri =
        trimTrailingSlash(enterpriseProperties.effectiveNotificationServiceUrl())
            + enterpriseProperties.effectiveNotificationDeadLetterPath()
            + (queryString.isBlank() ? "" : "?" + queryString);
    return getJson(uri, jwt);
  }

  public Mono<JsonNode> dryRun(UUID id) {
    String jwt = serviceJwtSigner.sign(NOTIFICATION_AUDIENCE, DEAD_LETTER_READ_SCOPE);
    String uri =
        trimTrailingSlash(enterpriseProperties.effectiveNotificationServiceUrl())
            + enterpriseProperties.effectiveNotificationDeadLetterPath()
            + "/"
            + id
            + "/requeue/dry-run";
    return postEmpty(uri, jwt);
  }

  public Mono<JsonNode> requeue(UUID id, String idempotencyKey, String reason, String actorUserId) {
    String jwt = serviceJwtSigner.sign(NOTIFICATION_AUDIENCE, DEAD_LETTER_REQUEUE_SCOPE);
    String uri =
        trimTrailingSlash(enterpriseProperties.effectiveNotificationServiceUrl())
            + enterpriseProperties.effectiveNotificationDeadLetterPath()
            + "/"
            + id
            + "/requeue";
    Map<String, String> body = Map.of("idempotencyKey", idempotencyKey, "reason", reason);
    try {
      return webClient
          .post()
          .uri(uri)
          .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
          .header(ADMIN_ACTOR_HEADER, actorUserId)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(body)
          .retrieve()
          .bodyToMono(String.class)
          .timeout(Duration.ofSeconds(15))
          .map(this::parseJson)
          .onErrorResume(
              e -> {
                log.warn(
                    "notification_dead_letter_requeue_proxy_failed message={}",
                    e instanceof WebClientResponseException w
                        ? w.getStatusCode().value() + " " + w.getResponseBodyAsString()
                        : e.getMessage());
                return Mono.error(e);
              });
    } catch (RuntimeException e) {
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
        .timeout(Duration.ofSeconds(15))
        .map(this::parseJson)
        .onErrorResume(
            e -> {
              log.warn(
                  "notification_dead_letter_list_proxy_failed message={}",
                  e instanceof WebClientResponseException w
                      ? w.getStatusCode().value() + " " + w.getResponseBodyAsString()
                      : e.getMessage());
              return Mono.error(e);
            });
  }

  private Mono<JsonNode> postEmpty(String uri, String jwt) {
    return webClient
        .post()
        .uri(uri)
        .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(15))
        .map(this::parseJson)
        .onErrorResume(
            e -> {
              log.warn(
                  "notification_dead_letter_dry_run_proxy_failed message={}",
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

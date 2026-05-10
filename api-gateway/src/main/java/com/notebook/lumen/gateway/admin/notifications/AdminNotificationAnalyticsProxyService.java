package com.notebook.lumen.gateway.admin.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayAdminEnterpriseProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
public class AdminNotificationAnalyticsProxyService {
  private static final Logger log = LoggerFactory.getLogger(AdminNotificationAnalyticsProxyService.class);
  static final String ANALYTICS_SCOPE = "internal:admin:notifications:analytics:read";
  private static final String NOTIFICATION_AUDIENCE = "notification-service";

  private final WebClient webClient;
  private final ServiceJwtSigner serviceJwtSigner;
  private final GatewayAdminEnterpriseProperties enterpriseProperties;
  private final ObjectMapper objectMapper;

  public AdminNotificationAnalyticsProxyService(
      WebClient.Builder webClientBuilder,
      ServiceJwtSigner auditServiceJwtSigner,
      GatewayAdminEnterpriseProperties enterpriseProperties,
      ObjectMapper objectMapper) {
    this.webClient = webClientBuilder.build();
    this.serviceJwtSigner = auditServiceJwtSigner;
    this.enterpriseProperties = enterpriseProperties;
    this.objectMapper = objectMapper;
  }

  public Mono<JsonNode> fetchSummary(Instant from, Instant to, String bucket) {
    String base = enterpriseProperties.effectiveNotificationServiceUrl();
    String jwt = serviceJwtSigner.sign(NOTIFICATION_AUDIENCE, ANALYTICS_SCOPE);
    String path = enterpriseProperties.effectiveNotificationAnalyticsPath();
    StringBuilder uri =
        new StringBuilder(trimTrailingSlash(base))
            .append(path)
            .append("?from=")
            .append(from)
            .append("&to=")
            .append(to);
    if (bucket != null && !bucket.isBlank()) {
      uri.append("&bucket=").append(bucket);
    }
    return webClient
        .get()
        .uri(uri.toString())
        .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(10))
        .map(this::parseJson)
        .onErrorResume(
            e -> {
              log.warn(
                  "notification_analytics_proxy_failed message={}",
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

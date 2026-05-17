package com.notebook.lumen.gateway.admin.retention;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayWorkspaceRetentionProperties;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
public class WorkspaceRetentionClient {

  static final String READ_SCOPE = "internal:admin:retention:read";
  static final String WORKSPACE_AUDIENCE = "workspace-service";

  private final GatewayWorkspaceRetentionProperties properties;
  private final ServiceJwtSigner serviceJwtSigner;
  private final WebClient webClient;

  public WorkspaceRetentionClient(
      GatewayWorkspaceRetentionProperties properties,
      ServiceJwtSigner serviceJwtSigner,
      WebClient.Builder webClientBuilder) {
    this.properties = properties;
    this.serviceJwtSigner = serviceJwtSigner;
    this.webClient = webClientBuilder.build();
  }

  public boolean enabled() {
    return properties.enabled() && properties.url() != null && !properties.url().isBlank();
  }

  public Mono<Map<String, Object>> fetchPlan(Collection<String> legalHoldScopes, String requestId) {
    if (!enabled()) {
      return Mono.empty();
    }
    UriComponentsBuilder ub =
        UriComponentsBuilder.fromUriString(baseUrl()).queryParam("dryRun", true);
    if (legalHoldScopes != null && !legalHoldScopes.isEmpty()) {
      ub.queryParam("legalHoldScopes", String.join(",", legalHoldScopes));
    }
    String url = ub.build(true).toUriString();
    return Mono.defer(
        () -> {
          String jwt;
          try {
            jwt = serviceJwtSigner.sign(WORKSPACE_AUDIENCE, READ_SCOPE);
          } catch (RuntimeException e) {
            return Mono.error(e);
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
              .bodyToMono(MAP_TYPE)
              .timeout(Duration.ofMillis(properties.timeoutMs()));
        });
  }

  private String baseUrl() {
    String base = properties.url();
    if (base == null || base.isBlank()) base = "http://workspace-service:8082";
    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    return base + "/internal/admin/retention/workspace/plan";
  }

  private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>>
      MAP_TYPE = new org.springframework.core.ParameterizedTypeReference<>() {};
}

package com.notebook.lumen.gateway.security;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayAuditProxyProperties;
import com.notebook.lumen.gateway.config.GatewayBreakGlassProperties;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BreakGlassDenylistClient {
  private static final String AUDIENCE = "identity-service";
  private static final String SCOPE = "internal:break-glass:token:check";

  private final GatewayAuditProxyProperties auditProxyProperties;
  private final GatewayBreakGlassProperties properties;
  private final ServiceJwtSigner signer;
  private final WebClient webClient;
  private final ConcurrentHashMap<String, CachedStatus> cache = new ConcurrentHashMap<>();

  public BreakGlassDenylistClient(
      GatewayAuditProxyProperties auditProxyProperties,
      GatewayBreakGlassProperties properties,
      ServiceJwtSigner signer,
      WebClient.Builder webClientBuilder) {
    this.auditProxyProperties = auditProxyProperties;
    this.properties = properties;
    this.signer = signer;
    this.webClient = webClientBuilder.build();
  }

  public DenylistStatus isRevoked(String jti) {
    if (jti == null || jti.isBlank()) {
      return new DenylistStatus(false, false);
    }
    CachedStatus cached = cache.get(jti);
    if (cached != null && Instant.now().isBefore(cached.cachedUntil())) {
      return new DenylistStatus(cached.revoked(), true);
    }
    String jwt = signer.sign(AUDIENCE, SCOPE);
    @SuppressWarnings("unchecked")
    Map<String, Object> body =
        webClient
            .get()
            .uri(baseUrl() + "/" + jti + "/revoked")
            .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
            .retrieve()
            .bodyToMono(Map.class)
            .block();
    boolean revoked = body != null && Boolean.TRUE.equals(body.get("revoked"));
    cache.put(
        jti,
        new CachedStatus(
            revoked, Instant.now().plusSeconds(Math.max(1, properties.denylistCacheSeconds()))));
    return new DenylistStatus(revoked, false);
  }

  private String baseUrl() {
    String base = auditProxyProperties.identityServiceUrl();
    if (base == null || base.isBlank()) {
      base = "http://identity-service:8081";
    }
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/internal/break-glass/tokens";
  }

  public record DenylistStatus(boolean revoked, boolean cacheHit) {}

  private record CachedStatus(boolean revoked, Instant cachedUntil) {}
}

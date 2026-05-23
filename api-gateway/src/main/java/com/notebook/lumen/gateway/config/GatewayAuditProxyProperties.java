package com.notebook.lumen.gateway.config;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.audit-proxy")
public record GatewayAuditProxyProperties(
    String identityServiceUrl,
    String workspaceServiceUrl,
    String contentServiceUrl,
    String internalPath,
    ServiceJwt serviceJwt) {

  public String effectiveInternalPath() {
    if (internalPath == null || internalPath.isBlank()) {
      return "/internal/audit-events";
    }
    return internalPath.startsWith("/") ? internalPath : "/" + internalPath;
  }

  public ServiceJwtProperties signerProperties() {
    ServiceJwt jwt =
        serviceJwt == null
            ? new ServiceJwt(null, null, null, null, null, null, null, null)
            : serviceJwt;
    String activeKid =
        jwt.activeKid() == null || jwt.activeKid().isBlank()
            ? "gateway-admin-audit-key-1"
            : jwt.activeKid();
    return new ServiceJwtProperties(
        activeKid,
        jwt.privateKey(),
        jwt.privateKeyPath(),
        jwt.issuer() == null || jwt.issuer().isBlank() ? "audit-admin" : jwt.issuer(),
        jwt.subject() == null || jwt.subject().isBlank() ? "service:api-gateway" : jwt.subject(),
        jwt.serviceName() == null || jwt.serviceName().isBlank()
            ? "api-gateway"
            : jwt.serviceName(),
        Duration.ofSeconds(
            jwt.ttlSeconds() == null || jwt.ttlSeconds() <= 0 ? 60 : jwt.ttlSeconds()));
  }

  public record ServiceJwt(
      String activeKid,
      String privateKey,
      String privateKeyPath,
      String issuer,
      String subject,
      String serviceName,
      String audience,
      Long ttlSeconds) {}
}

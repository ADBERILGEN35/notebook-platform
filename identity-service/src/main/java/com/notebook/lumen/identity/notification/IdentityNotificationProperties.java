package com.notebook.lumen.identity.notification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.notification")
public record IdentityNotificationProperties(
    boolean enabled, String serviceUrl, long timeoutMs, ServiceJwt serviceJwt) {
  public record ServiceJwt(
      String activeKid,
      String privateKey,
      String privateKeyPath,
      String issuer,
      String subject,
      String serviceName,
      long ttlSeconds,
      String audience) {
    public boolean signingConfigured() {
      return hasText(privateKey) || hasText(privateKeyPath);
    }

    public Duration ttl() {
      return Duration.ofSeconds(ttlSeconds <= 0 ? 60 : ttlSeconds);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}

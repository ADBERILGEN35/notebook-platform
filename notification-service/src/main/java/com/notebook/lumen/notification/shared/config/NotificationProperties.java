package com.notebook.lumen.notification.shared.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(Email email, Internal internal) {
  public record Email(
      String provider,
      String from,
      boolean workerEnabled,
      int maxAttempts,
      long retryInitialDelaySeconds,
      long retryMaxDelaySeconds,
      long workerFixedDelayMs,
      Smtp smtp) {}

  public record Smtp(String host, int port, String username, String password, boolean tlsEnabled) {}

  public record Internal(TrustedService trustedNotificationClient) {}

  public record TrustedService(
      String kid,
      String publicKey,
      String publicKeyPath,
      String issuer,
      String audience,
      long clockSkewSeconds,
      String allowedScopes) {
    public boolean configured() {
      return hasText(publicKey) || hasText(publicKeyPath);
    }

    public Duration clockSkew() {
      return Duration.ofSeconds(clockSkewSeconds <= 0 ? 5 : clockSkewSeconds);
    }

    public Set<String> allowedScopeSet() {
      if (!hasText(allowedScopes)) {
        return Set.of();
      }
      return Arrays.stream(allowedScopes.split(","))
          .map(String::trim)
          .filter(scope -> !scope.isBlank())
          .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean hasText(String value) {
      return value != null && !value.isBlank();
    }
  }
}

package com.notebook.lumen.search.shared.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public record SearchProperties(
    int maxIndexedChars,
    int maxQueryLength,
    int minQueryLength,
    int maxPageSize,
    Workspace workspace,
    ServiceJwt serviceJwt,
    Internal internal) {
  public record Workspace(String serviceUrl, long timeoutMs, int retryMaxAttempts) {}

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
  }

  public record Internal(TrustedService trustedIndexingClient) {}

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
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}

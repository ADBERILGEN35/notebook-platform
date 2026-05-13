package com.notebook.lumen.content.admin.retention;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content.retention.admin")
public record ContentRetentionAdminProperties(
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

package com.notebook.lumen.content.config;

import com.notebook.lumen.common.security.secrets.SecretValue;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content")
public record ContentProperties(
    String workerInstanceId,
    Blocks blocks,
    Concurrency concurrency,
    Workspace workspace,
    ServiceJwt serviceJwt,
    Search search) {

  public record Blocks(boolean allowUnknownBlockTypes, int maxDepth, int maxJsonBytes) {}

  public record Concurrency(boolean requireIfMatchForNoteUpdate) {}

  public record Workspace(
      String serviceUrl,
      long timeoutMs,
      float circuitBreakerFailureThreshold,
      long circuitBreakerOpenStateMs,
      int retryMaxAttempts,
      String internalApiToken,
      String internalApiTokenPrimary,
      String internalApiTokenSecondary,
      String internalAuthMode) {
    public String effectiveInternalApiToken() {
      return internalApiTokenPrimary != null && !internalApiTokenPrimary.isBlank()
          ? internalApiTokenPrimary
          : internalApiToken;
    }

    public SecretValue effectiveInternalApiTokenSecret() {
      return SecretValue.of("WORKSPACE_INTERNAL_API_TOKEN_PRIMARY", effectiveInternalApiToken());
    }

    public boolean primaryTokenConfigured() {
      return internalApiTokenPrimary != null && !internalApiTokenPrimary.isBlank();
    }

    public boolean legacyTokenConfigured() {
      return internalApiToken != null && !internalApiToken.isBlank();
    }

    public boolean secondaryTokenConfigured() {
      return internalApiTokenSecondary != null && !internalApiTokenSecondary.isBlank();
    }
  }

  public record ServiceJwt(
      String activeKid,
      String privateKey,
      String privateKeyPath,
      String publicKeyPath,
      String issuer,
      String subject,
      String serviceName,
      long ttlSeconds,
      String audience) {
    public boolean signingConfigured() {
      return (privateKey != null && !privateKey.isBlank())
          || (privateKeyPath != null && !privateKeyPath.isBlank());
    }
  }

  public record Search(
      String serviceUrl,
      long timeoutMs,
      boolean enabled,
      SearchServiceJwt serviceJwt,
      SearchIndexSource source,
      SearchOutbox outbox) {}

  public record SearchServiceJwt(
      String activeKid,
      String privateKey,
      String privateKeyPath,
      String issuer,
      String subject,
      String serviceName,
      long ttlSeconds,
      String audience) {
    public boolean signingConfigured() {
      return (privateKey != null && !privateKey.isBlank())
          || (privateKeyPath != null && !privateKeyPath.isBlank());
    }
  }

  public record SearchIndexSource(TrustedService trustedSearchService) {}

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

  public record SearchOutbox(
      boolean workerEnabled,
      int batchSize,
      int maxAttempts,
      long initialDelaySeconds,
      long maxDelaySeconds,
      long pollIntervalSeconds,
      long lockTimeoutSeconds,
      SearchOutboxAdmin admin) {
    public int effectiveBatchSize() {
      return batchSize <= 0 ? 50 : batchSize;
    }

    public int effectiveMaxAttempts() {
      return maxAttempts <= 0 ? 10 : maxAttempts;
    }

    public long effectiveInitialDelaySeconds() {
      return initialDelaySeconds <= 0 ? 30 : initialDelaySeconds;
    }

    public long effectiveMaxDelaySeconds() {
      return maxDelaySeconds <= 0 ? 3600 : maxDelaySeconds;
    }

    public long effectivePollIntervalSeconds() {
      return pollIntervalSeconds <= 0 ? 10 : pollIntervalSeconds;
    }

    public long effectiveLockTimeoutSeconds() {
      return lockTimeoutSeconds <= 0 ? 300 : lockTimeoutSeconds;
    }
  }

  public record SearchOutboxAdmin(
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

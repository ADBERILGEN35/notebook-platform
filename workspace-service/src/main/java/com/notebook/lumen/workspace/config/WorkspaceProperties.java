package com.notebook.lumen.workspace.config;

import com.notebook.lumen.common.security.secrets.SecretValue;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace")
public record WorkspaceProperties(
    Invitations invitations,
    Identity identity,
    Internal internal,
    Notification notification,
    Search search) {
  public record Invitations(long ttlDays, String acceptBaseUrl, boolean exposeTokenInResponse) {}

  public record Identity(
      String serviceUrl,
      long timeoutMs,
      float circuitBreakerFailureThreshold,
      long circuitBreakerOpenStateMs,
      int retryMaxAttempts) {}

  public record Internal(
      String apiToken,
      String primaryToken,
      String secondaryToken,
      String authMode,
      TrustedService trustedContentService,
      TrustedService trustedSearchService,
      TrustedService trustedNotificationService) {
    public boolean tokenRequired() {
      return hasText(primaryToken) || hasText(apiToken);
    }

    public String effectivePrimaryToken() {
      return hasText(primaryToken) ? primaryToken : apiToken;
    }

    public SecretValue effectivePrimarySecret() {
      return SecretValue.of("INTERNAL_API_TOKEN_PRIMARY", effectivePrimaryToken());
    }

    public SecretValue secondarySecret() {
      return SecretValue.of("INTERNAL_API_TOKEN_SECONDARY", secondaryToken);
    }

    public boolean primaryTokenConfigured() {
      return hasText(primaryToken);
    }

    public boolean legacyTokenConfigured() {
      return hasText(apiToken);
    }

    public boolean secondaryTokenConfigured() {
      return hasText(secondaryToken);
    }

    public boolean serviceJwtTrustConfigured() {
      return (trustedContentService != null && trustedContentService.configured())
          || (trustedSearchService != null && trustedSearchService.configured())
          || (trustedNotificationService != null && trustedNotificationService.configured());
    }

    private static boolean hasText(String value) {
      return value != null && !value.isBlank();
    }
  }

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

  public record Notification(
      String serviceUrl, long timeoutMs, boolean enabled, ServiceJwt serviceJwt) {}

  public record Search(
      String serviceUrl, long timeoutMs, boolean permissionRefreshEnabled, ServiceJwt serviceJwt) {}

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

    private static boolean hasText(String value) {
      return value != null && !value.isBlank();
    }
  }
}

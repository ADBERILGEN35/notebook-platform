package com.notebook.lumen.workspace.config;

import com.notebook.lumen.common.security.secrets.SecretValue;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace")
public record WorkspaceProperties(Invitations invitations, Identity identity, Internal internal) {
  public record Invitations(long ttlDays, String acceptBaseUrl, boolean exposeTokenInResponse) {}

  public record Identity(
      String serviceUrl,
      long timeoutMs,
      float circuitBreakerFailureThreshold,
      long circuitBreakerOpenStateMs,
      int retryMaxAttempts) {}

  public record Internal(String apiToken, String primaryToken, String secondaryToken) {
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

    private static boolean hasText(String value) {
      return value != null && !value.isBlank();
    }
  }
}

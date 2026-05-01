package com.notebook.lumen.content.config;

import com.notebook.lumen.common.security.secrets.SecretValue;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content")
public record ContentProperties(Blocks blocks, Workspace workspace, ServiceJwt serviceJwt) {
  public record Blocks(boolean allowUnknownBlockTypes, int maxDepth, int maxJsonBytes) {}

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
}

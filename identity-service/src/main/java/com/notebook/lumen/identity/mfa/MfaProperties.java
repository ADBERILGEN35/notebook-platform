package com.notebook.lumen.identity.mfa;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.mfa")
public record MfaProperties(
    boolean enabled, Webauthn webauthn, int challengeTtlSeconds, boolean requiredForPlatformAdmin) {
  public record Webauthn(
      boolean enabled,
      String rpId,
      String rpName,
      String allowedOrigins,
      String requireUserVerification) {}

  public boolean webauthnEnabled() {
    return webauthn != null && webauthn.enabled();
  }
}

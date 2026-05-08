package com.notebook.lumen.identity.siem;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.siem")
public record SiemProperties(
    boolean pushEnabled,
    String provider,
    String endpointUrl,
    String authMode,
    String bearerToken,
    String customHeaderName,
    String customHeaderValue,
    int timeoutSeconds,
    int batchSize,
    int maxAttempts,
    int backoffBaseSeconds,
    int backoffMaxSeconds,
    boolean workerEnabled,
    int workerPollIntervalSeconds,
    int outboxRetentionDays,
    int deadRetentionDays,
    boolean includeLoginSuccess) {

  public String effectiveProvider() {
    return provider == null || provider.isBlank() ? "noop" : provider.trim().toLowerCase();
  }

  public String effectiveAuthMode() {
    return authMode == null || authMode.isBlank() ? "none" : authMode.trim().toLowerCase();
  }
}

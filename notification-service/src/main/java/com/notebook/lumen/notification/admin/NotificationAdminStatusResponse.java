package com.notebook.lumen.notification.admin;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationAdminStatusResponse(
    InApp inApp,
    Sse sse,
    Digest digest,
    Email email,
    Webhook webhook,
    Suppression suppression,
    boolean unavailable,
    String unavailableReason) {

  public record InApp(boolean enabled) {}

  public record Sse(
      boolean enabled, boolean distributedFanoutEnabled, boolean redisChannelConfigured) {}

  public record Digest(boolean enabled, boolean workerEnabled) {}

  public record Email(String provider, boolean providerConfigured) {}

  public record Webhook(boolean enabled) {}

  public record Suppression(boolean enabled) {}
}

package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.platform-retention.notification")
public record GatewayNotificationRetentionProperties(boolean enabled, String url, long timeoutMs) {

  public GatewayNotificationRetentionProperties {
    if (timeoutMs <= 0) {
      timeoutMs = 3_000;
    }
  }
}

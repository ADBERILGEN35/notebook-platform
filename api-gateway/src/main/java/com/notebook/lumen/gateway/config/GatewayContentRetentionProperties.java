package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.platform-retention.content")
public record GatewayContentRetentionProperties(boolean enabled, String url, long timeoutMs) {

  public GatewayContentRetentionProperties {
    if (timeoutMs <= 0) {
      timeoutMs = 3_000;
    }
  }
}

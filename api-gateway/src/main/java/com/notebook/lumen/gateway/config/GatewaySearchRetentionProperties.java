package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.platform-retention.search")
public record GatewaySearchRetentionProperties(boolean enabled, String url, long timeoutMs) {

  public GatewaySearchRetentionProperties {
    if (timeoutMs <= 0) {
      timeoutMs = 3_000;
    }
  }
}

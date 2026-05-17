package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.platform-retention.workspace")
public record GatewayWorkspaceRetentionProperties(boolean enabled, String url, long timeoutMs) {

  public GatewayWorkspaceRetentionProperties {
    if (timeoutMs <= 0) {
      timeoutMs = 3_000;
    }
  }
}

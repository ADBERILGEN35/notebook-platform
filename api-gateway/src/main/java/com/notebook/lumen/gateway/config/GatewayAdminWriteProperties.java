package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.write")
public record GatewayAdminWriteProperties(boolean enabled, String internalPath) {

  public String effectiveInternalPath() {
    if (internalPath == null || internalPath.isBlank()) {
      return "/internal/admin/change-requests";
    }
    return internalPath.startsWith("/") ? internalPath : "/" + internalPath;
  }
}

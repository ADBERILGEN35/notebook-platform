package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.rbac-visibility")
public record GatewayAdminRbacVisibilityProperties(String internalPath) {

  public String effectiveInternalPath() {
    if (internalPath == null || internalPath.isBlank()) {
      return "/internal/admin/rbac";
    }
    return internalPath.startsWith("/") ? internalPath : "/" + internalPath;
  }
}

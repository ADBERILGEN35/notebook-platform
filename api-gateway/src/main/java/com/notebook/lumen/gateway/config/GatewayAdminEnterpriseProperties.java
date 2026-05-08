package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.enterprise")
public record GatewayAdminEnterpriseProperties(
    String notificationServiceUrl,
    String identityStatusPath,
    String notificationStatusPath) {

  public String effectiveNotificationServiceUrl() {
    if (notificationServiceUrl == null || notificationServiceUrl.isBlank()) {
      return "http://notification-service:8084";
    }
    return notificationServiceUrl.trim();
  }

  public String effectiveIdentityStatusPath() {
    if (identityStatusPath == null || identityStatusPath.isBlank()) {
      return "/internal/admin/status/identity-security";
    }
    return identityStatusPath.startsWith("/") ? identityStatusPath : "/" + identityStatusPath;
  }

  public String effectiveNotificationStatusPath() {
    if (notificationStatusPath == null || notificationStatusPath.isBlank()) {
      return "/internal/admin/status/notification";
    }
    return notificationStatusPath.startsWith("/")
        ? notificationStatusPath
        : "/" + notificationStatusPath;
  }
}

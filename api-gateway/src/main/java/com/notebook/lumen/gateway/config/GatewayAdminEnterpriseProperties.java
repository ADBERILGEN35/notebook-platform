package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.enterprise")
public record GatewayAdminEnterpriseProperties(
    String notificationServiceUrl,
    String identityStatusPath,
    String notificationStatusPath,
    String notificationAnalyticsPath,
    String notificationDeadLetterPath,
    String notificationRetentionPath,
    String notificationLegalHoldPath,
    String contentStatusPath) {

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

  public String effectiveNotificationAnalyticsPath() {
    if (notificationAnalyticsPath == null || notificationAnalyticsPath.isBlank()) {
      return "/internal/admin/notifications/analytics/summary";
    }
    return notificationAnalyticsPath.startsWith("/")
        ? notificationAnalyticsPath
        : "/" + notificationAnalyticsPath;
  }

  public String effectiveNotificationDeadLetterPath() {
    if (notificationDeadLetterPath == null || notificationDeadLetterPath.isBlank()) {
      return "/internal/admin/notifications/dead-letter";
    }
    return notificationDeadLetterPath.startsWith("/")
        ? notificationDeadLetterPath
        : "/" + notificationDeadLetterPath;
  }

  public String effectiveNotificationRetentionPath() {
    if (notificationRetentionPath == null || notificationRetentionPath.isBlank()) {
      return "/internal/admin/notifications/retention";
    }
    return notificationRetentionPath.startsWith("/")
        ? notificationRetentionPath
        : "/" + notificationRetentionPath;
  }

  public String effectiveNotificationLegalHoldPath() {
    if (notificationLegalHoldPath == null || notificationLegalHoldPath.isBlank()) {
      return "/internal/admin/notifications/legal-holds";
    }
    return notificationLegalHoldPath.startsWith("/")
        ? notificationLegalHoldPath
        : "/" + notificationLegalHoldPath;
  }

  public String effectiveContentStatusPath() {
    if (contentStatusPath == null || contentStatusPath.isBlank()) {
      return "/internal/admin/status/content";
    }
    return contentStatusPath.startsWith("/") ? contentStatusPath : "/" + contentStatusPath;
  }
}

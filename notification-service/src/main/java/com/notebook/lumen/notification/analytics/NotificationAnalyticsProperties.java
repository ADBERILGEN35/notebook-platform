package com.notebook.lumen.notification.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.analytics")
public record NotificationAnalyticsProperties(
    boolean enabled,
    int retentionDays,
    int maxRangeDays,
    String defaultBucket) {

  public NotificationAnalyticsProperties {
    if (retentionDays <= 0) {
      retentionDays = 90;
    }
    if (maxRangeDays <= 0) {
      maxRangeDays = 30;
    }
    if (defaultBucket == null || defaultBucket.isBlank()) {
      defaultBucket = "hour";
    }
  }
}

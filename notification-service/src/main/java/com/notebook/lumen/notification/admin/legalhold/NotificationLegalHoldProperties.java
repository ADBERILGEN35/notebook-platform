package com.notebook.lumen.notification.admin.legalhold;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.legal-hold")
public record NotificationLegalHoldProperties(boolean enabled, boolean adminApiEnabled, int maxActive) {

  public NotificationLegalHoldProperties {
    if (maxActive <= 0) {
      maxActive = 50;
    }
  }
}

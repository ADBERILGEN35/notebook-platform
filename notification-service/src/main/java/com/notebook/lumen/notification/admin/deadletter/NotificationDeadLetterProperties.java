package com.notebook.lumen.notification.admin.deadletter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.dead-letter")
public record NotificationDeadLetterProperties(
    int maxRequeueCount, int pageMaxSize, String recipientHashPepper) {

  public NotificationDeadLetterProperties {
    if (maxRequeueCount <= 0) {
      maxRequeueCount = 3;
    }
    if (pageMaxSize <= 0) {
      pageMaxSize = 200;
    }
    if (pageMaxSize > 500) {
      pageMaxSize = 500;
    }
  }
}

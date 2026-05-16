package com.notebook.lumen.notification.admin.platformretention;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.platform-retention")
public record NotificationPlatformRetentionProperties(
    boolean dryRunCountsEnabled,
    int maxCountQueryLimit,
    int analyticsRetentionDays,
    int fanoutSentRetentionDays,
    int fanoutDeadRetentionDays,
    int deadLetterRequeueRetentionDays,
    int digestTerminalRetentionDays,
    int emailTerminalRetentionDays) {

  public NotificationPlatformRetentionProperties {
    if (maxCountQueryLimit <= 0) {
      maxCountQueryLimit = 100_000;
    }
    if (analyticsRetentionDays <= 0) {
      analyticsRetentionDays = 90;
    }
    if (fanoutSentRetentionDays <= 0) {
      fanoutSentRetentionDays = 7;
    }
    if (fanoutDeadRetentionDays <= 0) {
      fanoutDeadRetentionDays = 90;
    }
    if (deadLetterRequeueRetentionDays <= 0) {
      deadLetterRequeueRetentionDays = 90;
    }
    if (digestTerminalRetentionDays <= 0) {
      digestTerminalRetentionDays = 90;
    }
    if (emailTerminalRetentionDays <= 0) {
      emailTerminalRetentionDays = 90;
    }
  }

  public int retentionDaysFor(NotificationPlatformRetentionTargetKey target) {
    return switch (target) {
      case NOTIFICATION_ANALYTICS_HOURLY -> analyticsRetentionDays;
      case NOTIFICATION_FANOUT_OUTBOX_SENT -> fanoutSentRetentionDays;
      case NOTIFICATION_FANOUT_OUTBOX_DEAD -> fanoutDeadRetentionDays;
      case NOTIFICATION_DEAD_LETTER_REQUEUE_REQUESTS -> deadLetterRequeueRetentionDays;
      case NOTIFICATION_DIGEST_ITEMS_TERMINAL -> digestTerminalRetentionDays;
      case NOTIFICATION_EMAIL_NOTIFICATIONS_TERMINAL -> emailTerminalRetentionDays;
    };
  }
}

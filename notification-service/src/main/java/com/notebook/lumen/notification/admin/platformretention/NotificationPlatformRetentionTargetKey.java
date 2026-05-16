package com.notebook.lumen.notification.admin.platformretention;

import java.util.Optional;

public enum NotificationPlatformRetentionTargetKey {
  NOTIFICATION_ANALYTICS_HOURLY(
      "notification.analytics_hourly", NotificationPlatformRetentionTargetStatus.DRY_RUN_READY),
  NOTIFICATION_FANOUT_OUTBOX_SENT(
      "notification.fanout_outbox_sent", NotificationPlatformRetentionTargetStatus.DRY_RUN_READY),
  NOTIFICATION_FANOUT_OUTBOX_DEAD(
      "notification.fanout_outbox_dead", NotificationPlatformRetentionTargetStatus.DRY_RUN_READY),
  NOTIFICATION_DEAD_LETTER_REQUEUE_REQUESTS(
      "notification.dead_letter_requeue_requests",
      NotificationPlatformRetentionTargetStatus.DRY_RUN_READY),
  NOTIFICATION_DIGEST_ITEMS_TERMINAL(
      "notification.digest_items_terminal",
      NotificationPlatformRetentionTargetStatus.DRY_RUN_READY),
  NOTIFICATION_EMAIL_NOTIFICATIONS_TERMINAL(
      "notification.email_notifications_terminal",
      NotificationPlatformRetentionTargetStatus.DRY_RUN_READY);

  private final String key;
  private final NotificationPlatformRetentionTargetStatus defaultStatus;

  NotificationPlatformRetentionTargetKey(
      String key, NotificationPlatformRetentionTargetStatus defaultStatus) {
    this.key = key;
    this.defaultStatus = defaultStatus;
  }

  public String key() {
    return key;
  }

  public NotificationPlatformRetentionTargetStatus defaultStatus() {
    return defaultStatus;
  }

  public static Optional<NotificationPlatformRetentionTargetKey> fromKey(String raw) {
    if (raw == null) return Optional.empty();
    String trimmed = raw.trim();
    for (NotificationPlatformRetentionTargetKey value : values()) {
      if (value.key.equals(trimmed)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}

package com.notebook.lumen.notification.admin.platformretention;

import java.util.Optional;

/**
 * Legal-hold scopes recognized by the notification platform retention plan. Platform-level scopes
 * ({@code ALL_PLATFORM}, {@code NOTIFICATION}) are passed from the gateway after reading active
 * identity-service platform legal holds; notification-domain scopes mirror {@code
 * com.notebook.lumen.notification.admin.legalhold.LegalHoldScope} names so a single query parameter
 * can carry either source.
 */
public enum NotificationPlatformRetentionLegalHoldScope {
  ALL_PLATFORM(true),
  NOTIFICATION(true),
  ALL_NOTIFICATION_RETENTION(true),
  FANOUT_OUTBOX(false),
  DEAD_LETTER_REQUEUE_REQUESTS(false),
  ANALYTICS(false),
  DIGEST_ITEMS(false),
  EMAIL_NOTIFICATIONS(false);

  private final boolean fullyBlocking;

  NotificationPlatformRetentionLegalHoldScope(boolean fullyBlocking) {
    this.fullyBlocking = fullyBlocking;
  }

  public boolean fullyBlocking() {
    return fullyBlocking;
  }

  public boolean blocks(NotificationPlatformRetentionTargetKey target) {
    if (fullyBlocking) {
      return true;
    }
    return switch (this) {
      case FANOUT_OUTBOX ->
          target == NotificationPlatformRetentionTargetKey.NOTIFICATION_FANOUT_OUTBOX_SENT
              || target == NotificationPlatformRetentionTargetKey.NOTIFICATION_FANOUT_OUTBOX_DEAD;
      case DEAD_LETTER_REQUEUE_REQUESTS ->
          target
              == NotificationPlatformRetentionTargetKey.NOTIFICATION_DEAD_LETTER_REQUEUE_REQUESTS;
      case ANALYTICS ->
          target == NotificationPlatformRetentionTargetKey.NOTIFICATION_ANALYTICS_HOURLY;
      case DIGEST_ITEMS ->
          target == NotificationPlatformRetentionTargetKey.NOTIFICATION_DIGEST_ITEMS_TERMINAL;
      case EMAIL_NOTIFICATIONS ->
          target
              == NotificationPlatformRetentionTargetKey.NOTIFICATION_EMAIL_NOTIFICATIONS_TERMINAL;
      default -> false;
    };
  }

  public static Optional<NotificationPlatformRetentionLegalHoldScope> fromString(String raw) {
    if (raw == null) return Optional.empty();
    String trimmed = raw.trim().toUpperCase();
    for (NotificationPlatformRetentionLegalHoldScope value : values()) {
      if (value.name().equals(trimmed)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}

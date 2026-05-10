package com.notebook.lumen.notification.admin.legalhold;

import com.notebook.lumen.notification.admin.retention.RetentionPurgeKind;
import java.util.Arrays;
import java.util.Locale;

public enum LegalHoldScope {
  ALL_NOTIFICATION_RETENTION,
  FANOUT_OUTBOX,
  DEAD_LETTER_REQUEUE_REQUESTS,
  ANALYTICS,
  DIGEST_ITEMS,
  EMAIL_NOTIFICATIONS;

  public static LegalHoldScope parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("scope is required");
    }
    String n = raw.trim().toUpperCase(Locale.ROOT);
    for (LegalHoldScope s : values()) {
      if (s.name().equals(n)) {
        return s;
      }
    }
    throw new IllegalArgumentException(
        "Unknown scope; allowed: "
            + Arrays.stream(values()).map(Enum::name).reduce((a, b) -> a + ", " + b).orElse(""));
  }

  public boolean blocks(RetentionPurgeKind kind) {
    return switch (this) {
      case ALL_NOTIFICATION_RETENTION -> true;
      case FANOUT_OUTBOX ->
          kind == RetentionPurgeKind.NOTIFICATION_FANOUT_OUTBOX_SENT
              || kind == RetentionPurgeKind.NOTIFICATION_FANOUT_OUTBOX_DEAD;
      case DEAD_LETTER_REQUEUE_REQUESTS ->
          kind == RetentionPurgeKind.NOTIFICATION_DEAD_LETTER_REQUEUE_REQUESTS;
      case ANALYTICS -> kind == RetentionPurgeKind.NOTIFICATION_DELIVERY_ANALYTICS_HOURLY;
      case DIGEST_ITEMS -> kind == RetentionPurgeKind.NOTIFICATION_DIGEST_ITEMS_TERMINAL;
      case EMAIL_NOTIFICATIONS -> kind == RetentionPurgeKind.EMAIL_NOTIFICATIONS_TERMINAL;
    };
  }
}

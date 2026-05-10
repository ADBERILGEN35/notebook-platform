package com.notebook.lumen.notification.admin.retention;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Ordered purge steps; metric {@link #metricTag} is low-cardinality. */
public enum RetentionPurgeKind {
  NOTIFICATION_DELIVERY_ANALYTICS_HOURLY(
      "notification_delivery_analytics_hourly", "analytics_hourly"),
  NOTIFICATION_DEAD_LETTER_REQUEUE_REQUESTS(
      "notification_dead_letter_requeue_requests", "dead_letter_requeue_requests"),
  NOTIFICATION_DIGEST_ITEMS_TERMINAL("notification_digest_items_terminal", "digest_items_terminal"),
  EMAIL_NOTIFICATIONS_TERMINAL("email_notifications_terminal", "email_notifications_terminal"),
  NOTIFICATION_FANOUT_OUTBOX_SENT("notification_fanout_outbox_sent", "fanout_outbox_sent"),
  NOTIFICATION_FANOUT_OUTBOX_DEAD("notification_fanout_outbox_dead", "fanout_outbox_dead");

  private final String apiTargetKey;
  private final String metricTag;

  RetentionPurgeKind(String apiTargetKey, String metricTag) {
    this.apiTargetKey = apiTargetKey;
    this.metricTag = metricTag;
  }

  public String apiTargetKey() {
    return apiTargetKey;
  }

  public String metricTag() {
    return metricTag;
  }

  public static Optional<RetentionPurgeKind> tryFromApiTargetKey(String apiTargetKey) {
    if (apiTargetKey == null || apiTargetKey.isBlank()) {
      return Optional.empty();
    }
    for (RetentionPurgeKind k : values()) {
      if (k.apiTargetKey.equals(apiTargetKey)) {
        return Optional.of(k);
      }
    }
    return Optional.empty();
  }

  public static Set<RetentionPurgeKind> parseTargets(String raw) {
    if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw.trim())) {
      return EnumSet.allOf(RetentionPurgeKind.class);
    }
    for (RetentionPurgeKind k : values()) {
      if (k.apiTargetKey.equalsIgnoreCase(raw.trim())) {
        return EnumSet.of(k);
      }
    }
    for (RetentionPurgeKind k : values()) {
      if (k.name().equalsIgnoreCase(raw.trim())) {
        return EnumSet.of(k);
      }
    }
    throw new IllegalArgumentException(
        "Unknown retention target; use ALL or one of: "
            + Arrays.stream(values()).map(RetentionPurgeKind::apiTargetKey).collect(Collectors.joining(", ")));
  }
}

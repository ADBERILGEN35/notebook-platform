package com.notebook.lumen.notification.analytics;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationAnalyticsRecorder {
  private static final Logger log = LoggerFactory.getLogger(NotificationAnalyticsRecorder.class);
  private static final ZoneOffset UTC = ZoneOffset.UTC;

  private final NotificationAnalyticsProperties properties;
  private final NotificationAnalyticsUpsertRepository upsertRepository;
  private final MeterRegistry meterRegistry;

  public NotificationAnalyticsRecorder(
      NotificationAnalyticsProperties properties,
      NotificationAnalyticsUpsertRepository upsertRepository,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.upsertRepository = upsertRepository;
    this.meterRegistry = meterRegistry;
  }

  public void record(
      NotificationAnalyticsEventKind eventKind,
      String notificationType,
      String channel,
      String severity,
      long delta) {
    if (!properties.enabled() || delta <= 0) {
      return;
    }
    try {
      Instant now = Instant.now();
      Instant bucketStart = now.atZone(UTC).truncatedTo(ChronoUnit.HOURS).toInstant();
      upsertRepository.increment(
          bucketStart,
          "notification-service",
          emptyToBlank(notificationType),
          emptyToBlank(channel),
          emptyToBlank(severity),
          eventKind,
          delta,
          now);
      meterRegistry
          .counter("notification_analytics_events_recorded_total", "eventKind", eventKind.name())
          .increment(delta);
      if (eventKind == NotificationAnalyticsEventKind.CREATED) {
        meterRegistry
            .counter(
                "notification_delivery_created_total",
                "type",
                tag(emptyToBlank(notificationType)),
                "channel",
                tag(emptyToBlank(channel)),
                "severity",
                tag(emptyToBlank(severity)))
            .increment(delta);
      }
      if (eventKind == NotificationAnalyticsEventKind.SKIPPED_PREFERENCE
          || eventKind == NotificationAnalyticsEventKind.SKIPPED_WORKSPACE_PREFERENCE) {
        meterRegistry
            .counter(
                "notification_delivery_skipped_total",
                "reason",
                eventKind.name(),
                "channel",
                tag(emptyToBlank(channel)))
            .increment(delta);
      }
      if (eventKind == NotificationAnalyticsEventKind.FAILED || eventKind == NotificationAnalyticsEventKind.DEAD) {
        meterRegistry
            .counter(
                "notification_delivery_failed_total",
                "channel",
                tag(emptyToBlank(channel)),
                "reason",
                eventKind.name())
            .increment(delta);
      }
    } catch (RuntimeException e) {
      log.warn("notification_analytics_record_failed eventKind={}", eventKind, e);
      meterRegistry.counter("notification_analytics_record_failures_total").increment();
    }
  }

  private static String emptyToBlank(String v) {
    return v == null || v.isBlank() ? "" : v.trim();
  }

  private static String tag(String v) {
    return v.isBlank() ? "unspecified" : v;
  }
}

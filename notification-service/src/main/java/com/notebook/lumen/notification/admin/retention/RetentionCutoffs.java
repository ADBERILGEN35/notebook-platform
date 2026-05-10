package com.notebook.lumen.notification.admin.retention;

import com.notebook.lumen.notification.analytics.NotificationAnalyticsProperties;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record RetentionCutoffs(
    Instant analyticsHourly,
    Instant fanoutSent,
    Instant fanoutDead,
    Instant deadLetterRequeue,
    Instant digestTerminal,
    Instant emailTerminal) {

  static RetentionCutoffs compute(
      NotificationAnalyticsProperties analytics,
      NotificationRetentionProperties retention,
      NotificationProperties notification,
      Instant now) {
    return new RetentionCutoffs(
        now.minus(analytics.retentionDays(), ChronoUnit.DAYS),
        now.minus(notification.fanout().sentRetentionHours(), ChronoUnit.HOURS),
        now.minus(notification.fanout().deadRetentionDays(), ChronoUnit.DAYS),
        now.minus(retention.deadLetterRequeueRequestRetentionDays(), ChronoUnit.DAYS),
        now.minus(retention.digestSentRetentionDays(), ChronoUnit.DAYS),
        now.minus(retention.emailTerminalRetentionDays(), ChronoUnit.DAYS));
  }
}

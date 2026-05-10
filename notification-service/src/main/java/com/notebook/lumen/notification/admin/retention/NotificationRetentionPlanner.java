package com.notebook.lumen.notification.admin.retention;

import com.notebook.lumen.notification.admin.legalhold.NotificationLegalHoldBlockEvaluator;
import com.notebook.lumen.notification.admin.legalhold.NotificationLegalHoldEntity;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsProperties;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationRetentionPlanner {

  private final NotificationAnalyticsProperties analyticsProperties;
  private final NotificationRetentionProperties retentionProperties;
  private final NotificationProperties notificationProperties;
  private final NotificationRetentionJdbcRepository jdbc;
  private final NotificationLegalHoldBlockEvaluator legalHoldBlockEvaluator;

  public NotificationRetentionPlanner(
      NotificationAnalyticsProperties analyticsProperties,
      NotificationRetentionProperties retentionProperties,
      NotificationProperties notificationProperties,
      NotificationRetentionJdbcRepository jdbc,
      NotificationLegalHoldBlockEvaluator legalHoldBlockEvaluator) {
    this.analyticsProperties = analyticsProperties;
    this.retentionProperties = retentionProperties;
    this.notificationProperties = notificationProperties;
    this.jdbc = jdbc;
    this.legalHoldBlockEvaluator = legalHoldBlockEvaluator;
  }

  public RetentionAdminDtos.RetentionPlanResponse plan(Instant now, boolean dryRun) {
    List<RetentionAdminDtos.RetentionPlanTarget> targets = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<NotificationLegalHoldEntity> activeHolds = legalHoldBlockEvaluator.loadActiveHolds();

    long fanoutDeadDays = notificationProperties.fanout().deadRetentionDays();
    if (fanoutDeadDays > 0 && fanoutDeadDays < 90) {
      warnings.add(
          "Fanout DEAD retention is under 90 days; operators may lose dead-letter history before review.");
    }
    if (analyticsProperties.retentionDays() < 7) {
      warnings.add("Analytics retention under 7 days may make dashboards sparse.");
    }

    var cutoffs = RetentionCutoffs.compute(analyticsProperties, retentionProperties, notificationProperties, now);
    var analytics = jdbc.countAnalyticsHourlyEligible(cutoffs.analyticsHourly());
    targets.add(
        buildTarget(
            RetentionPurgeKind.NOTIFICATION_DELIVERY_ANALYTICS_HOURLY,
            analytics.count(),
            analyticsProperties.retentionDays() + "d",
            NotificationRetentionJdbcRepository.oldestOrEmpty(analytics).orElse(null),
            cutoffs.analyticsHourly(),
            activeHolds,
            now));

    var fanoutSent = jdbc.countFanoutSentEligible(cutoffs.fanoutSent());
    targets.add(
        buildTarget(
            RetentionPurgeKind.NOTIFICATION_FANOUT_OUTBOX_SENT,
            fanoutSent.count(),
            notificationProperties.fanout().sentRetentionHours() + "h",
            NotificationRetentionJdbcRepository.oldestOrEmpty(fanoutSent).orElse(null),
            cutoffs.fanoutSent(),
            activeHolds,
            now));

    var fanoutDead = jdbc.countFanoutDeadEligible(cutoffs.fanoutDead());
    targets.add(
        buildTarget(
            RetentionPurgeKind.NOTIFICATION_FANOUT_OUTBOX_DEAD,
            fanoutDead.count(),
            fanoutDeadDays + "d",
            NotificationRetentionJdbcRepository.oldestOrEmpty(fanoutDead).orElse(null),
            cutoffs.fanoutDead(),
            activeHolds,
            now));

    var requeue = jdbc.countDeadLetterRequeueEligible(cutoffs.deadLetterRequeue());
    targets.add(
        buildTarget(
            RetentionPurgeKind.NOTIFICATION_DEAD_LETTER_REQUEUE_REQUESTS,
            requeue.count(),
            retentionProperties.deadLetterRequeueRequestRetentionDays() + "d",
            NotificationRetentionJdbcRepository.oldestOrEmpty(requeue).orElse(null),
            cutoffs.deadLetterRequeue(),
            activeHolds,
            now));

    var digest =
        jdbc.countDigestTerminalEligible(cutoffs.digestTerminal(), cutoffs.digestTerminal());
    targets.add(
        buildTarget(
            RetentionPurgeKind.NOTIFICATION_DIGEST_ITEMS_TERMINAL,
            digest.count(),
            retentionProperties.digestSentRetentionDays() + "d",
            NotificationRetentionJdbcRepository.oldestOrEmpty(digest).orElse(null),
            cutoffs.digestTerminal(),
            activeHolds,
            now));

    var email = jdbc.countEmailTerminalEligible(cutoffs.emailTerminal());
    targets.add(
        buildTarget(
            RetentionPurgeKind.EMAIL_NOTIFICATIONS_TERMINAL,
            email.count(),
            retentionProperties.emailTerminalRetentionDays() + "d",
            NotificationRetentionJdbcRepository.oldestOrEmpty(email).orElse(null),
            cutoffs.emailTerminal(),
            activeHolds,
            now));

    targets.sort(Comparator.comparing(RetentionAdminDtos.RetentionPlanTarget::target));
    return new RetentionAdminDtos.RetentionPlanResponse(now, dryRun, List.copyOf(targets), List.copyOf(warnings));
  }

  private RetentionAdminDtos.RetentionPlanTarget buildTarget(
      RetentionPurgeKind kind,
      long eligibleCount,
      String retention,
      Instant oldestEligibleAt,
      Instant cutoff,
      List<NotificationLegalHoldEntity> activeHolds,
      Instant now) {
    var hr = legalHoldBlockEvaluator.evaluate(kind, activeHolds, now);
    long purgeable = hr.blocked() ? 0 : eligibleCount;
    return new RetentionAdminDtos.RetentionPlanTarget(
        kind.apiTargetKey(),
        eligibleCount,
        retention,
        oldestEligibleAt,
        cutoff,
        hr.blocked(),
        List.copyOf(hr.activeHoldKeys()),
        purgeable,
        List.copyOf(hr.warnings()));
  }
}

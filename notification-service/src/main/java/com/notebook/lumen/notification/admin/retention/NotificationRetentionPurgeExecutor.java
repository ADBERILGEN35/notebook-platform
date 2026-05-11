package com.notebook.lumen.notification.admin.retention;

import com.notebook.lumen.notification.admin.legalhold.LegalHoldAuditEventType;
import com.notebook.lumen.notification.admin.legalhold.NotificationLegalHoldBlockEvaluator;
import com.notebook.lumen.notification.admin.legalhold.NotificationLegalHoldMetrics;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsProperties;
import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationRetentionPurgeExecutor {

  /** Safe delete order: aggregates and audit-adjacent rows before outbox terminal rows. */
  private static final List<RetentionPurgeKind> ORDER =
      List.of(
          RetentionPurgeKind.NOTIFICATION_DELIVERY_ANALYTICS_HOURLY,
          RetentionPurgeKind.NOTIFICATION_DEAD_LETTER_REQUEUE_REQUESTS,
          RetentionPurgeKind.NOTIFICATION_DIGEST_ITEMS_TERMINAL,
          RetentionPurgeKind.EMAIL_NOTIFICATIONS_TERMINAL,
          RetentionPurgeKind.NOTIFICATION_FANOUT_OUTBOX_SENT,
          RetentionPurgeKind.NOTIFICATION_FANOUT_OUTBOX_DEAD);

  private final NotificationAnalyticsProperties analyticsProperties;
  private final NotificationRetentionProperties retentionProperties;
  private final NotificationProperties notificationProperties;
  private final NotificationRetentionPurgeBatches batches;
  private final NotificationRetentionMetrics metrics;
  private final NotificationLegalHoldBlockEvaluator legalHoldBlockEvaluator;
  private final AuditService auditService;
  private final NotificationLegalHoldMetrics legalHoldMetrics;

  public NotificationRetentionPurgeExecutor(
      NotificationAnalyticsProperties analyticsProperties,
      NotificationRetentionProperties retentionProperties,
      NotificationProperties notificationProperties,
      NotificationRetentionPurgeBatches batches,
      NotificationRetentionMetrics metrics,
      NotificationLegalHoldBlockEvaluator legalHoldBlockEvaluator,
      AuditService auditService,
      NotificationLegalHoldMetrics legalHoldMetrics) {
    this.analyticsProperties = analyticsProperties;
    this.retentionProperties = retentionProperties;
    this.notificationProperties = notificationProperties;
    this.batches = batches;
    this.metrics = metrics;
    this.legalHoldBlockEvaluator = legalHoldBlockEvaluator;
    this.auditService = auditService;
    this.legalHoldMetrics = legalHoldMetrics;
  }

  /**
   * Deletes eligible rows until no more batches or {@code maxTotal} reached. Returns per-target
   * deleted counts.
   */
  public Map<String, Long> purge(Set<RetentionPurgeKind> kinds, Instant now, int maxTotal) {
    var cutoffs =
        RetentionCutoffs.compute(
            analyticsProperties, retentionProperties, notificationProperties, now);
    Map<String, Long> out = new LinkedHashMap<>();
    int remaining = maxTotal;
    var activeHolds = legalHoldBlockEvaluator.loadActiveHolds();
    for (RetentionPurgeKind kind : ORDER) {
      if (!kinds.contains(kind)) {
        continue;
      }
      metrics.recordPlan(kind);
      if (legalHoldBlockEvaluator.isBlocked(kind, activeHolds)) {
        out.put(kind.apiTargetKey(), 0L);
        legalHoldMetrics.recordRetentionBlocked(kind);
        var hr = legalHoldBlockEvaluator.evaluate(kind, activeHolds, now);
        auditService.record(
            LegalHoldAuditEventType.RETENTION_BLOCKED,
            "NOTIFICATION_RETENTION",
            UUID.randomUUID(),
            Map.of(
                "target",
                kind.apiTargetKey(),
                "blockedByLegalHold",
                true,
                "activeHoldKeys",
                String.join(",", hr.activeHoldKeys())));
        continue;
      }
      long deletedForKind = 0;
      while (remaining > 0) {
        int batch = Math.min(retentionProperties.batchSize(), remaining);
        int deleted;
        try {
          deleted = batches.deleteBatch(kind, cutoffs, batch);
        } catch (RuntimeException ex) {
          metrics.recordFailure(kind);
          throw ex;
        }
        if (deleted == 0) {
          break;
        }
        deletedForKind += deleted;
        remaining -= deleted;
        metrics.recordDeleted(kind, deleted);
        if (deleted < batch) {
          break;
        }
      }
      out.put(kind.apiTargetKey(), deletedForKind);
    }
    return out;
  }
}

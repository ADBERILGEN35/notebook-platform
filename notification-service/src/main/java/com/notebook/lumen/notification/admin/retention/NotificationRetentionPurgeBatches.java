package com.notebook.lumen.notification.admin.retention;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationRetentionPurgeBatches {

  private final NotificationRetentionJdbcRepository jdbc;

  public NotificationRetentionPurgeBatches(NotificationRetentionJdbcRepository jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int deleteBatch(RetentionPurgeKind kind, RetentionCutoffs cutoffs, int limit) {
    return switch (kind) {
      case NOTIFICATION_DELIVERY_ANALYTICS_HOURLY ->
          jdbc.deleteAnalyticsHourlyBatch(cutoffs.analyticsHourly(), limit);
      case NOTIFICATION_DEAD_LETTER_REQUEUE_REQUESTS ->
          jdbc.deleteDeadLetterRequeueBatch(cutoffs.deadLetterRequeue(), limit);
      case NOTIFICATION_DIGEST_ITEMS_TERMINAL ->
          jdbc.deleteDigestTerminalBatch(cutoffs.digestTerminal(), cutoffs.digestTerminal(), limit);
      case EMAIL_NOTIFICATIONS_TERMINAL ->
          jdbc.deleteEmailTerminalBatch(cutoffs.emailTerminal(), limit);
      case NOTIFICATION_FANOUT_OUTBOX_SENT ->
          jdbc.deleteFanoutSentBatch(cutoffs.fanoutSent(), limit);
      case NOTIFICATION_FANOUT_OUTBOX_DEAD ->
          jdbc.deleteFanoutDeadBatch(cutoffs.fanoutDead(), limit);
    };
  }
}

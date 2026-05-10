package com.notebook.lumen.notification.analytics;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificationAnalyticsUpsertRepository {

  @PersistenceContext private EntityManager entityManager;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void increment(
      Instant bucketStart,
      String sourceService,
      String notificationType,
      String channel,
      String severity,
      NotificationAnalyticsEventKind eventKind,
      long delta,
      Instant now) {
    String nt = notificationType == null ? "" : notificationType;
    String ch = channel == null ? "" : channel;
    String sev = severity == null ? "" : severity;
    entityManager
        .createNativeQuery(
            """
            insert into notification_delivery_analytics_hourly
              (id, bucket_start, source_service, notification_type, channel, severity, event_kind, count, created_at, updated_at)
            values (:id, :bucketStart, :sourceService, :notificationType, :channel, :severity, :eventKind, :delta, :now, :now)
            on conflict on constraint uq_notification_delivery_analytics_dims
            do update set
              count = notification_delivery_analytics_hourly.count + excluded.count,
              updated_at = excluded.updated_at
            """)
        .setParameter("id", UUID.randomUUID())
        .setParameter("bucketStart", bucketStart)
        .setParameter("sourceService", sourceService == null ? "notification-service" : sourceService)
        .setParameter("notificationType", nt)
        .setParameter("channel", ch)
        .setParameter("severity", sev)
        .setParameter("eventKind", eventKind.name())
        .setParameter("delta", delta)
        .setParameter("now", now)
        .executeUpdate();
  }
}

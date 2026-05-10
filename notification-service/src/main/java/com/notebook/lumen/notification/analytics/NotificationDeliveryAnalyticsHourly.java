package com.notebook.lumen.notification.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_delivery_analytics_hourly")
public class NotificationDeliveryAnalyticsHourly {

  @Id private UUID id;

  @Column(name = "bucket_start", nullable = false)
  private Instant bucketStart;

  @Column(name = "source_service", nullable = false, length = 64)
  private String sourceService;

  @Column(name = "notification_type", nullable = false, length = 128)
  private String notificationType;

  @Column(name = "channel", nullable = false, length = 32)
  private String channel;

  @Column(name = "severity", nullable = false, length = 32)
  private String severity;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_kind", nullable = false, length = 64)
  private NotificationAnalyticsEventKind eventKind;

  @Column(name = "count", nullable = false)
  private long count;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected NotificationDeliveryAnalyticsHourly() {}

  public NotificationDeliveryAnalyticsHourly(
      UUID id,
      Instant bucketStart,
      String sourceService,
      String notificationType,
      String channel,
      String severity,
      NotificationAnalyticsEventKind eventKind,
      long count,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.bucketStart = bucketStart;
    this.sourceService = sourceService;
    this.notificationType = notificationType;
    this.channel = channel;
    this.severity = severity;
    this.eventKind = eventKind;
    this.count = count;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public Instant getBucketStart() {
    return bucketStart;
  }

  public String getNotificationType() {
    return notificationType;
  }

  public String getChannel() {
    return channel;
  }

  public String getSeverity() {
    return severity;
  }

  public NotificationAnalyticsEventKind getEventKind() {
    return eventKind;
  }

  public long getCount() {
    return count;
  }
}

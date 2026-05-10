package com.notebook.lumen.notification.user.realtime;

import com.notebook.lumen.notification.analytics.NotificationAnalyticsEventKind;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsRecorder;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import com.notebook.lumen.notification.user.domain.UserNotification;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutbox;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationSseEventDispatcher {
  private static final Logger log = LoggerFactory.getLogger(NotificationSseEventDispatcher.class);

  private final NotificationSseBroker broker;
  private final NotificationSseDistributedPublisher distributedPublisher;
  private final NotificationSseProperties sseProperties;
  private final NotificationInstanceIdProvider instanceIdProvider;
  private final MeterRegistry meterRegistry;
  private final NotificationAnalyticsRecorder analyticsRecorder;

  public NotificationSseEventDispatcher(
      NotificationSseBroker broker,
      NotificationSseDistributedPublisher distributedPublisher,
      NotificationSseProperties sseProperties,
      NotificationInstanceIdProvider instanceIdProvider,
      MeterRegistry meterRegistry,
      NotificationAnalyticsRecorder analyticsRecorder) {
    this.broker = broker;
    this.distributedPublisher = distributedPublisher;
    this.sseProperties = sseProperties;
    this.instanceIdProvider = instanceIdProvider;
    this.meterRegistry = meterRegistry;
    this.analyticsRecorder = analyticsRecorder;
  }

  public NotificationSseEventEnvelope buildCreatedEnvelope(UserNotification notification, long unreadCount) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("notificationId", notification.getId().toString());
    payload.put("type", notification.getType().name());
    payload.put("severity", notification.getSeverity().name());
    payload.put(
        "workspaceId",
        notification.getWorkspaceId() == null ? null : notification.getWorkspaceId().toString());
    payload.put("createdAt", notification.getCreatedAt().toString());
    payload.put("unreadCount", unreadCount);
    return new NotificationSseEventEnvelope(
        UUID.randomUUID(),
        instanceIdProvider.instanceId(),
        notification.getRecipientUserId(),
        "notification.created",
        payload,
        Instant.now());
  }

  public NotificationSseEventEnvelope buildReadEnvelope(
      UUID userId, UUID notificationId, long unreadCount) {
    return new NotificationSseEventEnvelope(
        UUID.randomUUID(),
        instanceIdProvider.instanceId(),
        userId,
        "notification.read",
        Map.of(
            "notificationId",
            notificationId.toString(),
            "updatedAt",
            Instant.now().toString(),
            "unreadCount",
            unreadCount),
        Instant.now());
  }

  public NotificationSseEventEnvelope buildArchivedEnvelope(
      UUID userId, UUID notificationId, long unreadCount) {
    return new NotificationSseEventEnvelope(
        UUID.randomUUID(),
        instanceIdProvider.instanceId(),
        userId,
        "notification.archived",
        Map.of(
            "notificationId",
            notificationId.toString(),
            "updatedAt",
            Instant.now().toString(),
            "unreadCount",
            unreadCount),
        Instant.now());
  }

  public NotificationSseEventEnvelope buildUnreadCountEnvelope(UUID userId, long unreadCount) {
    return new NotificationSseEventEnvelope(
        UUID.randomUUID(),
        instanceIdProvider.instanceId(),
        userId,
        "notification.unread_count",
        Map.of("unreadCount", unreadCount, "updatedAt", Instant.now().toString()),
        Instant.now());
  }

  public void publishCreated(UserNotification notification, long unreadCount) {
    dispatchAllowDistributedFailure(buildCreatedEnvelope(notification, unreadCount));
  }

  public void publishRead(UUID userId, UUID notificationId, long unreadCount) {
    dispatchAllowDistributedFailure(buildReadEnvelope(userId, notificationId, unreadCount));
  }

  public void publishArchived(UUID userId, UUID notificationId, long unreadCount) {
    dispatchAllowDistributedFailure(buildArchivedEnvelope(userId, notificationId, unreadCount));
  }

  public void publishUnreadCount(UUID userId, long unreadCount) {
    dispatchAllowDistributedFailure(buildUnreadCountEnvelope(userId, unreadCount));
  }

  /**
   * Request-path dispatch: local broker + distributed publish; distributed failures are swallowed so
   * DB transactions are not rolled back after commit (Faz 64).
   */
  public void dispatchAllowDistributedFailure(NotificationSseEventEnvelope envelope) {
    dispatch(envelope, false);
  }

  /**
   * Outbox worker: distributed publish failures propagate so rows can retry or dead-letter.
   */
  public void dispatchStrictDistributed(NotificationSseEventEnvelope envelope) {
    dispatch(envelope, true);
  }

  public void deliverFromDistributed(NotificationSseEventEnvelope envelope) {
    int delivered =
        broker.deliverToUser(envelope.recipientUserId(), envelope.eventType(), envelope.payload());
    if (delivered > 0) {
      meterRegistry.counter("notifications_sse_distributed_delivered_total").increment();
    }
  }

  /** Rehydrate envelope from durable outbox row (same eventId; origin from worker instance). */
  public NotificationSseEventEnvelope envelopeFromOutbox(
      NotificationFanoutOutbox row, String originInstanceId) {
    return new NotificationSseEventEnvelope(
        row.getEventId(),
        originInstanceId,
        row.getRecipientUserId(),
        row.getEventType(),
        row.getPayload(),
        row.getCreatedAt());
  }

  private void dispatch(NotificationSseEventEnvelope envelope, boolean throwOnDistributedFailure) {
    if (sseProperties.getDistributed().isPublishLocalFirst()) {
      broker.deliverToUser(envelope.recipientUserId(), envelope.eventType(), envelope.payload());
    }
    publishDistributed(envelope, throwOnDistributedFailure);
  }

  private void publishDistributed(NotificationSseEventEnvelope envelope, boolean throwOnFailure) {
    if (!sseProperties.getDistributed().isEnabled()) {
      meterRegistry
          .counter("notifications_sse_distributed_published_total", "status", "disabled")
          .increment();
      return;
    }
    try {
      distributedPublisher.publish(envelope);
      analyticsRecorder.record(NotificationAnalyticsEventKind.REDIS_FANOUT_PUBLISH_SUCCESS, "", "", "", 1);
      meterRegistry
          .counter("notifications_sse_distributed_published_total", "status", "success")
          .increment();
    } catch (RuntimeException e) {
      analyticsRecorder.record(NotificationAnalyticsEventKind.REDIS_FANOUT_PUBLISH_FAILURE, "", "", "", 1);
      meterRegistry
          .counter("notifications_sse_distributed_published_total", "status", "failure")
          .increment();
      meterRegistry.counter("notifications_sse_distributed_publish_failures_total").increment();
      if (throwOnFailure) {
        throw e;
      }
      log.warn(
          "Distributed SSE publish failed (non-fatal for request path) eventId={}",
          envelope.eventId(),
          e);
    }
  }
}

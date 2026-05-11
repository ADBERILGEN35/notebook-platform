package com.notebook.lumen.notification.user.realtime;

import com.notebook.lumen.notification.analytics.NotificationAnalyticsEventKind;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsRecorder;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class NotificationSseBroker {
  private static final Logger log = LoggerFactory.getLogger(NotificationSseBroker.class);
  private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByUser =
      new ConcurrentHashMap<>();
  private final NotificationSseProperties sseProperties;
  private final MeterRegistry meterRegistry;
  private final NotificationAnalyticsRecorder analyticsRecorder;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "notification-sse-heartbeat"));

  public NotificationSseBroker(
      NotificationSseProperties sseProperties,
      MeterRegistry meterRegistry,
      NotificationAnalyticsRecorder analyticsRecorder) {
    this.sseProperties = sseProperties;
    this.meterRegistry = meterRegistry;
    this.analyticsRecorder = analyticsRecorder;
    long heartbeat = Math.max(1, sseProperties.getHeartbeatSeconds());
    scheduler.scheduleWithFixedDelay(
        this::publishHeartbeat, heartbeat, heartbeat, TimeUnit.SECONDS);
  }

  public SseEmitter connect(UUID userId) {
    if (!sseProperties.isEnabled()) {
      throw new IllegalStateException("SSE is disabled");
    }
    CopyOnWriteArrayList<SseEmitter> emitters =
        emittersByUser.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>());
    int maxConnections = Math.max(1, sseProperties.getMaxConnectionsPerUser());
    if (emitters.size() >= maxConnections) {
      meterRegistry
          .counter("notifications_sse_connections_rejected_total", "reason", "max_connections")
          .increment();
      throw new IllegalStateException("Too many active SSE connections");
    }

    long timeout =
        sseProperties.getTimeoutSeconds() <= 0 ? 0 : sseProperties.getTimeoutSeconds() * 1000;
    SseEmitter emitter = new SseEmitter(timeout);
    emitters.add(emitter);
    meterRegistry.counter("notifications_sse_connected_total").increment();
    meterRegistry.gauge(
        "notifications_sse_connections_active", emittersByUser, this::activeConnections);

    emitter.onCompletion(() -> disconnect(userId, emitter));
    emitter.onTimeout(() -> disconnect(userId, emitter));
    emitter.onError(error -> disconnect(userId, emitter));
    sendInternal(
        userId, emitter, "connected", Map.of("connectedAt", Instant.now().toString()), false);
    return emitter;
  }

  public int deliverToUser(UUID userId, String eventType, Map<String, Object> payload) {
    return publishToUser(userId, eventType, payload);
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }

  private void publishHeartbeat() {
    for (Map.Entry<UUID, CopyOnWriteArrayList<SseEmitter>> entry : emittersByUser.entrySet()) {
      UUID userId = entry.getKey();
      for (SseEmitter emitter : entry.getValue()) {
        sendInternal(userId, emitter, "heartbeat", Map.of("ts", Instant.now().toString()), true);
      }
    }
  }

  private int publishToUser(UUID userId, String eventType, Map<String, Object> payload) {
    List<SseEmitter> emitters = emittersByUser.get(userId);
    if (emitters == null || emitters.isEmpty()) {
      return 0;
    }
    int delivered = 0;
    for (SseEmitter emitter : emitters) {
      if (sendInternal(userId, emitter, eventType, payload, false)) {
        delivered++;
      }
    }
    return delivered;
  }

  private boolean sendInternal(
      UUID userId,
      SseEmitter emitter,
      String eventType,
      Map<String, Object> payload,
      boolean heartbeat) {
    try {
      emitter.send(SseEmitter.event().name(eventType).data(payload));
      if (!heartbeat) {
        meterRegistry
            .counter("notifications_sse_events_sent_total", "eventType", eventType)
            .increment();
      }
      return true;
    } catch (IOException ex) {
      meterRegistry.counter("notifications_sse_send_failures_total").increment();
      analyticsRecorder.record(NotificationAnalyticsEventKind.SSE_SEND_FAILURE, "", "SSE", "", 1);
      disconnect(userId, emitter);
      log.debug("SSE send failed for user {} event {}", userId, eventType);
      return false;
    }
  }

  private void disconnect(UUID userId, SseEmitter emitter) {
    CopyOnWriteArrayList<SseEmitter> emitters = emittersByUser.get(userId);
    if (emitters == null) {
      return;
    }
    emitters.remove(emitter);
    meterRegistry.counter("notifications_sse_disconnected_total").increment();
    if (emitters.isEmpty()) {
      emittersByUser.remove(userId);
    }
  }

  private double activeConnections(Map<UUID, CopyOnWriteArrayList<SseEmitter>> current) {
    return current.values().stream().mapToInt(List::size).sum();
  }

  /** Live SSE connection count across all users (pod-local). */
  public int activeConnectionCount() {
    return (int) activeConnections(emittersByUser);
  }
}

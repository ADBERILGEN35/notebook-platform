package com.notebook.lumen.notification.user.fanout;

import com.notebook.lumen.common.security.worker.WorkerInstanceIds;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsEventKind;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsRecorder;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.user.realtime.NotificationSseEventDispatcher;
import com.notebook.lumen.notification.user.realtime.NotificationSseEventEnvelope;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationFanoutOutboxProcessor {
  private static final Logger log = LoggerFactory.getLogger(NotificationFanoutOutboxProcessor.class);

  private final NotificationFanoutOutboxRepository repository;
  private final NotificationProperties properties;
  private final NotificationSseEventDispatcher sseDispatcher;
  private final MeterRegistry meterRegistry;
  private final NotificationAnalyticsRecorder analyticsRecorder;
  private final Timer processTimer;
  private final String workerInstanceId;

  public NotificationFanoutOutboxProcessor(
      NotificationFanoutOutboxRepository repository,
      NotificationProperties properties,
      NotificationSseEventDispatcher sseDispatcher,
      MeterRegistry meterRegistry,
      NotificationAnalyticsRecorder analyticsRecorder) {
    this.repository = repository;
    this.properties = properties;
    this.sseDispatcher = sseDispatcher;
    this.meterRegistry = meterRegistry;
    this.analyticsRecorder = analyticsRecorder;
    this.processTimer =
        Timer.builder("notifications_fanout_outbox_duration_seconds")
            .publishPercentileHistogram()
            .register(meterRegistry);
    this.workerInstanceId =
        WorkerInstanceIds.resolve(properties.workerInstanceId(), "notification-fanout");
  }

  @Transactional
  public void processDue() {
    NotificationProperties.Fanout fanout = properties.fanout();
    if (!fanout.outboxEnabled() || !fanout.workerEnabled()) {
      return;
    }
    Instant now = Instant.now();
    recoverExpiredSending(now);
    List<NotificationFanoutOutbox> due =
        repository.findDuePendingForUpdate(
            NotificationFanoutOutboxStatus.PENDING.name(), now, effectiveBatchSize());
    if (!due.isEmpty()) {
      meterRegistry.counter("notifications_fanout_outbox_claimed_total").increment(due.size());
    }
    for (NotificationFanoutOutbox row : due) {
      row.markSending(
          workerInstanceId, now, now.plusSeconds(Math.max(1, fanout.lockTtlSeconds())));
    }
    for (NotificationFanoutOutbox row : due) {
      Timer.Sample sample = Timer.start(meterRegistry);
      try {
        NotificationSseEventEnvelope envelope = sseDispatcher.envelopeFromOutbox(row, workerInstanceId);
        sseDispatcher.dispatchStrictDistributed(envelope);
        row.markSent(now);
        analyticsRecorder.record(NotificationAnalyticsEventKind.FANOUT_SENT, "", "", "", 1);
        meterRegistry.counter("notifications_fanout_outbox_published_total", "result", "sent").increment();
        sample.stop(processTimer);
      } catch (RuntimeException e) {
        sample.stop(processTimer);
        log.debug("Fanout outbox publish failed id={} eventId={}", row.getId(), row.getEventId(), e);
        handleFailure(row, e, Instant.now());
      }
    }
  }

  private void recoverExpiredSending(Instant now) {
    List<NotificationFanoutOutbox> expired =
        repository.findExpiredSendingForUpdate(
            NotificationFanoutOutboxStatus.SENDING.name(), now, effectiveBatchSize());
    for (NotificationFanoutOutbox row : expired) {
      row.recoverStaleSending("Fanout sending lock expired", now);
    }
    if (!expired.isEmpty()) {
      meterRegistry
          .counter("notifications_fanout_outbox_stale_lock_recovered_total")
          .increment(expired.size());
    }
  }

  private void handleFailure(NotificationFanoutOutbox row, RuntimeException e, Instant now) {
    NotificationProperties.Fanout fanout = properties.fanout();
    int failures = row.getAttemptCount() + 1;
    String err = safeError(e);
    if (failures >= effectiveMaxAttempts(fanout)) {
      row.markDead(err, now);
      analyticsRecorder.record(NotificationAnalyticsEventKind.FANOUT_DEAD, "", "", "", 1);
      meterRegistry.counter("notifications_fanout_outbox_dead_total").increment();
      meterRegistry.counter("notifications_fanout_outbox_published_total", "result", "dead").increment();
      return;
    }
    row.markRetry(err, nextAttemptAt(failures, now, fanout), now, failures);
    meterRegistry.counter("notifications_fanout_outbox_retry_total").increment();
    meterRegistry.counter("notifications_fanout_outbox_published_total", "result", "retry_scheduled").increment();
  }

  private int effectiveBatchSize() {
    int b = properties.fanout().batchSize();
    return b <= 0 ? 100 : b;
  }

  private int effectiveMaxAttempts(NotificationProperties.Fanout fanout) {
    int m = fanout.maxAttempts();
    return m <= 0 ? 10 : m;
  }

  private Instant nextAttemptAt(int failureCount, Instant now, NotificationProperties.Fanout fanout) {
    long initialDelay = Math.max(1, fanout.backoffBaseSeconds());
    long maxDelay = Math.max(initialDelay, fanout.backoffMaxSeconds());
    long multiplier = 1L << Math.min(failureCount - 1, 10);
    long delay = Math.min(maxDelay, initialDelay * multiplier);
    return now.plusSeconds(delay);
  }

  private String safeError(RuntimeException e) {
    String message = e.getMessage();
    if (message == null || message.isBlank()) {
      return e.getClass().getSimpleName();
    }
    String combined = e.getClass().getSimpleName() + ": " + message;
    return combined.length() <= 2000 ? combined : combined.substring(0, 2000);
  }
}

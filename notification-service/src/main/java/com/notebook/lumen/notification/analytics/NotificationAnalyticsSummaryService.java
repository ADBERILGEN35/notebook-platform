package com.notebook.lumen.notification.analytics;

import com.notebook.lumen.notification.email.domain.NotificationDigestItemStatus;
import com.notebook.lumen.notification.email.infrastructure.NotificationDigestItemRepository;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutboxRepository;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutboxStatus;
import com.notebook.lumen.notification.user.realtime.NotificationSseBroker;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class NotificationAnalyticsSummaryService {

  private final NotificationAnalyticsProperties analyticsProperties;
  private final NotificationDeliveryAnalyticsHourlyRepository hourlyRepository;
  private final NotificationFanoutOutboxRepository fanoutOutboxRepository;
  private final NotificationDigestItemRepository digestItemRepository;
  private final NotificationProperties notificationProperties;
  private final NotificationSseBroker sseBroker;
  private final NotificationWorkerRunTimestamps workerRunTimestamps;
  private final MeterRegistry meterRegistry;

  public NotificationAnalyticsSummaryService(
      NotificationAnalyticsProperties analyticsProperties,
      NotificationDeliveryAnalyticsHourlyRepository hourlyRepository,
      NotificationFanoutOutboxRepository fanoutOutboxRepository,
      NotificationDigestItemRepository digestItemRepository,
      NotificationProperties notificationProperties,
      NotificationSseBroker sseBroker,
      NotificationWorkerRunTimestamps workerRunTimestamps,
      MeterRegistry meterRegistry) {
    this.analyticsProperties = analyticsProperties;
    this.hourlyRepository = hourlyRepository;
    this.fanoutOutboxRepository = fanoutOutboxRepository;
    this.digestItemRepository = digestItemRepository;
    this.notificationProperties = notificationProperties;
    this.sseBroker = sseBroker;
    this.workerRunTimestamps = workerRunTimestamps;
    this.meterRegistry = meterRegistry;
  }

  public NotificationAnalyticsSummaryResponse build(Instant from, Instant to) {
    String bucket = analyticsProperties.defaultBucket();
    Map<NotificationAnalyticsEventKind, Long> kindTotals = new EnumMap<>(NotificationAnalyticsEventKind.class);
    for (var row : hourlyRepository.sumByEventKind(from, to)) {
      kindTotals.put(row.getEventKind(), row.getTotal());
    }
    long createdRaw = kindTotals.getOrDefault(NotificationAnalyticsEventKind.CREATED, 0L);
    long queued = kindTotals.getOrDefault(NotificationAnalyticsEventKind.QUEUED, 0L);
    long createdTotal = createdRaw + queued;
    long sent = kindTotals.getOrDefault(NotificationAnalyticsEventKind.SENT, 0L);
    long failed = kindTotals.getOrDefault(NotificationAnalyticsEventKind.FAILED, 0L);
    long dead = kindTotals.getOrDefault(NotificationAnalyticsEventKind.DEAD, 0L);
    long skippedPref = kindTotals.getOrDefault(NotificationAnalyticsEventKind.SKIPPED_PREFERENCE, 0L);
    long skippedWs =
        kindTotals.getOrDefault(NotificationAnalyticsEventKind.SKIPPED_WORKSPACE_PREFERENCE, 0L);
    long skippedWsAdmin =
        kindTotals.getOrDefault(NotificationAnalyticsEventKind.SKIPPED_WORKSPACE_ADMIN_POLICY, 0L);
    long digestQueued = kindTotals.getOrDefault(NotificationAnalyticsEventKind.DIGEST_QUEUED, 0L);
    long digestSent = kindTotals.getOrDefault(NotificationAnalyticsEventKind.DIGEST_SENT, 0L);
    long quietHours = kindTotals.getOrDefault(NotificationAnalyticsEventKind.QUIET_HOURS_DELAYED, 0L);

    Map<String, ChannelAgg> channelMap = new HashMap<>();
    for (var row : hourlyRepository.sumByChannelAndEventKind(from, to)) {
      channelMap
          .computeIfAbsent(row.getChannel(), ignored -> new ChannelAgg())
          .add(row.getEventKind(), row.getTotal());
    }
    List<NotificationAnalyticsSummaryResponse.ChannelBreakdownRow> byChannel = new ArrayList<>();
    for (var e : channelMap.entrySet()) {
      ChannelAgg a = e.getValue();
      byChannel.add(
          new NotificationAnalyticsSummaryResponse.ChannelBreakdownRow(
              e.getKey(), a.created, a.queued, a.sent, a.failed));
    }
    byChannel.sort(Comparator.comparing(NotificationAnalyticsSummaryResponse.ChannelBreakdownRow::channel));

    Map<String, Long> typeCreated = new HashMap<>();
    for (var row : hourlyRepository.sumByTypeAndEventKind(from, to)) {
      if (row.getEventKind() == NotificationAnalyticsEventKind.CREATED) {
        typeCreated.merge(row.getNotificationType(), row.getTotal(), Long::sum);
      }
    }
    List<NotificationAnalyticsSummaryResponse.TypeBreakdownRow> byType = new ArrayList<>();
    for (var e : typeCreated.entrySet()) {
      byType.add(new NotificationAnalyticsSummaryResponse.TypeBreakdownRow(e.getKey(), e.getValue()));
    }
    byType.sort(
        Comparator.comparing(NotificationAnalyticsSummaryResponse.TypeBreakdownRow::notificationType));

    long fanoutPending = fanoutOutboxRepository.countByStatus(NotificationFanoutOutboxStatus.PENDING);
    long fanoutRetrying = fanoutOutboxRepository.countByStatus(NotificationFanoutOutboxStatus.SENDING);
    long fanoutDead = fanoutOutboxRepository.countByStatus(NotificationFanoutOutboxStatus.DEAD);

    long sseFailuresInRange =
        kindTotals.getOrDefault(NotificationAnalyticsEventKind.SSE_SEND_FAILURE, 0L);
    long sseEventsTotal = sseEventsSentMeterTotal();

    long redisOk =
        kindTotals.getOrDefault(NotificationAnalyticsEventKind.REDIS_FANOUT_PUBLISH_SUCCESS, 0L);
    long redisFail =
        kindTotals.getOrDefault(NotificationAnalyticsEventKind.REDIS_FANOUT_PUBLISH_FAILURE, 0L);
    long redisRecv =
        kindTotals.getOrDefault(NotificationAnalyticsEventKind.REDIS_FANOUT_SUBSCRIBER_RECEIVED, 0L);

    long digestPending = digestItemRepository.countByStatus(NotificationDigestItemStatus.PENDING);

    var totals =
        new NotificationAnalyticsSummaryResponse.Totals(
            createdTotal,
            sent,
            failed,
            dead,
            skippedPref + skippedWs + skippedWsAdmin,
            digestQueued,
            digestSent,
            quietHours);

    return new NotificationAnalyticsSummaryResponse(
        from,
        to,
        bucket,
        totals,
        byChannel,
        byType,
        new NotificationAnalyticsSummaryResponse.FanoutSnapshot(fanoutPending, fanoutRetrying, fanoutDead),
        new NotificationAnalyticsSummaryResponse.SseSnapshot(
            sseBroker.activeConnectionCount(), sseFailuresInRange, sseEventsTotal),
        new NotificationAnalyticsSummaryResponse.RedisFanoutSnapshot(redisOk, redisFail, redisRecv),
        new NotificationAnalyticsSummaryResponse.DigestSnapshot(
            digestPending,
            notificationProperties.digest().workerEnabled(),
            notificationProperties.digest().enabled()),
        new NotificationAnalyticsSummaryResponse.WorkerHealth(
            workerRunTimestamps.fanoutLastRun(),
            workerRunTimestamps.digestLastRun(),
            workerRunTimestamps.emailLastRun(),
            notificationProperties.fanout().workerEnabled()
                && notificationProperties.fanout().outboxEnabled(),
            notificationProperties.email().workerEnabled()));
  }

  private long sseEventsSentMeterTotal() {
    var c = meterRegistry.find("notifications_sse_events_sent_total").counter();
    return c == null ? 0L : (long) c.count();
  }

  public void validateRange(Instant from, Instant to) {
    if (from == null || to == null || !to.isAfter(from)) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "NOTIFICATION_ANALYTICS_INVALID_RANGE", "Invalid from/to range");
    }
    long maxDays = analyticsProperties.maxRangeDays();
    long days = ChronoUnit.DAYS.between(from.atZone(ZoneOffset.UTC), to.atZone(ZoneOffset.UTC));
    if (days > maxDays) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "NOTIFICATION_ANALYTICS_RANGE_TOO_LARGE",
          "Requested range exceeds maxRangeDays=" + maxDays);
    }
  }

  private static final class ChannelAgg {
    long created;
    long queued;
    long sent;
    long failed;

    void add(NotificationAnalyticsEventKind kind, long n) {
      switch (kind) {
        case CREATED -> created += n;
        case QUEUED -> queued += n;
        case SENT -> sent += n;
        case FAILED -> failed += n;
        default -> {}
      }
    }
  }
}

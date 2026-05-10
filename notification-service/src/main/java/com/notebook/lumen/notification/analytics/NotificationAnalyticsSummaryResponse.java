package com.notebook.lumen.notification.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationAnalyticsSummaryResponse(
    Instant from,
    Instant to,
    String bucket,
    Totals totals,
    List<ChannelBreakdownRow> byChannel,
    List<TypeBreakdownRow> byType,
    FanoutSnapshot fanout,
    SseSnapshot sse,
    RedisFanoutSnapshot redisFanout,
    DigestSnapshot digest,
    WorkerHealth workers) {

  public record Totals(
      long created,
      long sent,
      long failed,
      long dead,
      long skippedPreference,
      long digestQueued,
      long digestSent,
      long quietHoursDelayed) {}

  public record ChannelBreakdownRow(String channel, long created, long queued, long sent, long failed) {}

  public record TypeBreakdownRow(String notificationType, long created) {}

  public record FanoutSnapshot(long pending, long retrying, long dead) {}

  public record SseSnapshot(int activeConnections, long sendFailuresInRange, long eventsSentMeterTotal) {}

  public record RedisFanoutSnapshot(
      long publishSuccessInRange, long publishFailureInRange, long subscriberReceivedInRange) {}

  public record DigestSnapshot(long pendingItems, boolean workerEnabled, boolean digestEnabled) {}

  public record WorkerHealth(
      Instant fanoutWorkerLastRun,
      Instant digestWorkerLastRun,
      Instant emailWorkerLastRun,
      boolean fanoutWorkerEnabled,
      boolean emailWorkerEnabled) {}
}

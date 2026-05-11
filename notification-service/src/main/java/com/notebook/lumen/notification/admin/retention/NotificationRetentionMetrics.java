package com.notebook.lumen.notification.admin.retention;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class NotificationRetentionMetrics {

  private final MeterRegistry registry;
  private final Map<RetentionPurgeKind, Counter> deletedByTarget =
      new EnumMap<>(RetentionPurgeKind.class);
  private final Map<RetentionPurgeKind, Counter> failuresByTarget =
      new EnumMap<>(RetentionPurgeKind.class);
  private final Map<RetentionPurgeKind, Counter> planByTarget =
      new EnumMap<>(RetentionPurgeKind.class);
  private final AtomicLong lastRunEpochMs = new AtomicLong(0);
  private Counter workerRunSuccess;
  private Counter workerRunDryRunOnly;
  private Counter workerRunFailure;

  public NotificationRetentionMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  @PostConstruct
  void register() {
    for (RetentionPurgeKind k : RetentionPurgeKind.values()) {
      String tag = k.metricTag();
      planByTarget.put(
          k,
          Counter.builder("notification.retention.plan")
              .tag("target", tag)
              .description("Retention plan evaluations per target")
              .register(registry));
      deletedByTarget.put(
          k,
          Counter.builder("notification.retention.purge.deleted")
              .tag("target", tag)
              .description("Rows deleted by retention purge")
              .register(registry));
      failuresByTarget.put(
          k,
          Counter.builder("notification.retention.purge.failures")
              .tag("target", tag)
              .description("Failed purge batches per target")
              .register(registry));
    }
    Gauge.builder("notification.retention.last.run.timestamp", lastRunEpochMs, AtomicLong::get)
        .description("Epoch millis of last retention worker run (0 if never)")
        .register(registry);
    workerRunSuccess =
        Counter.builder("notification.retention.worker.runs")
            .tag("result", "success")
            .description("Scheduled retention worker outcomes")
            .register(registry);
    workerRunDryRunOnly =
        Counter.builder("notification.retention.worker.runs")
            .tag("result", "dry_run_only")
            .description("Scheduled retention worker outcomes")
            .register(registry);
    workerRunFailure =
        Counter.builder("notification.retention.worker.runs")
            .tag("result", "failure")
            .description("Scheduled retention worker outcomes")
            .register(registry);
  }

  public void recordPlan(RetentionPurgeKind kind) {
    planByTarget.get(kind).increment();
  }

  public void recordDeleted(RetentionPurgeKind kind, long n) {
    if (n > 0) {
      deletedByTarget.get(kind).increment(n);
    }
  }

  public void recordFailure(RetentionPurgeKind kind) {
    failuresByTarget.get(kind).increment();
  }

  public void recordWorkerRun(Instant finishedAt, String result) {
    lastRunEpochMs.set(finishedAt.toEpochMilli());
    switch (result) {
      case "success" -> workerRunSuccess.increment();
      case "dry_run_only" -> workerRunDryRunOnly.increment();
      case "failure" -> workerRunFailure.increment();
      default -> workerRunDryRunOnly.increment();
    }
  }
}

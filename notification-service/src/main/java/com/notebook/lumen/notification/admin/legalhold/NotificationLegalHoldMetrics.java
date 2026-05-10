package com.notebook.lumen.notification.admin.legalhold;

import com.notebook.lumen.notification.admin.retention.RetentionPurgeKind;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationLegalHoldMetrics {

  private final MeterRegistry registry;
  private final NotificationLegalHoldRepository holdRepository;
  private final Map<LegalHoldScope, Counter> createdByScope = new EnumMap<>(LegalHoldScope.class);
  private final Map<LegalHoldScope, Counter> releasedByScope = new EnumMap<>(LegalHoldScope.class);
  private final Map<RetentionPurgeKind, Counter> retentionBlockedByTarget = new EnumMap<>(RetentionPurgeKind.class);

  public NotificationLegalHoldMetrics(MeterRegistry registry, NotificationLegalHoldRepository holdRepository) {
    this.registry = registry;
    this.holdRepository = holdRepository;
  }

  @PostConstruct
  void register() {
    for (LegalHoldScope s : LegalHoldScope.values()) {
      String tag = s.name().toLowerCase(Locale.ROOT);
      Gauge.builder(
              "notification.legal_holds.active",
              holdRepository,
              repo -> repo.countByStatusAndScope(LegalHoldStatus.ACTIVE, s))
          .tag("scope", tag)
          .description("Active notification legal holds by scope")
          .register(registry);
      createdByScope.put(
          s,
          Counter.builder("notification.legal_hold.created")
              .tag("scope", tag)
              .description("Notification legal holds created")
              .register(registry));
      releasedByScope.put(
          s,
          Counter.builder("notification.legal_hold.released")
              .tag("scope", tag)
              .description("Notification legal holds released")
              .register(registry));
    }
    for (RetentionPurgeKind k : RetentionPurgeKind.values()) {
      retentionBlockedByTarget.put(
          k,
          Counter.builder("notification.retention.blocked_by_legal_hold")
              .tag("target", k.metricTag())
              .description("Retention purge batches skipped due to legal hold")
              .register(registry));
    }
  }

  public void recordCreated(LegalHoldScope scope) {
    createdByScope.get(scope).increment();
  }

  public void recordReleased(LegalHoldScope scope) {
    releasedByScope.get(scope).increment();
  }

  public void recordRetentionBlocked(RetentionPurgeKind kind) {
    retentionBlockedByTarget.get(kind).increment();
  }
}

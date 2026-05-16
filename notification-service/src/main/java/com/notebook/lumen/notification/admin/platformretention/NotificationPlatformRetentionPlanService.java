package com.notebook.lumen.notification.admin.platformretention;

import com.notebook.lumen.notification.admin.platformretention.NotificationPlatformRetentionCountRepository.CountResult;
import com.notebook.lumen.notification.admin.platformretention.NotificationPlatformRetentionDtos.NotificationPlatformRetentionPlanResponse;
import com.notebook.lumen.notification.admin.platformretention.NotificationPlatformRetentionDtos.NotificationPlatformRetentionTargetView;
import com.notebook.lumen.notification.audit.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class NotificationPlatformRetentionPlanService {

  static final String SERVICE_NAME = "notification-service";
  static final String AGGREGATE = "NOTIFICATION_RETENTION";

  static final String WARN_DRY_RUN_DISABLED = "NOTIFICATION_RETENTION_DRY_RUN_DISABLED";
  static final String WARN_LEGAL_HOLD_BLOCKED = "NOTIFICATION_RETENTION_LEGAL_HOLD_BLOCKED";
  static final String WARN_PARTIAL_LEGAL_HOLD_MAPPING =
      "NOTIFICATION_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING";
  static final String WARN_TARGET_INVENTORY_ONLY = "NOTIFICATION_RETENTION_TARGET_INVENTORY_ONLY";
  static final String WARN_QUERY_CAPPED = "NOTIFICATION_RETENTION_QUERY_CAPPED";
  static final String WARN_COUNT_FAILED = "NOTIFICATION_RETENTION_COUNT_FAILED";
  static final String WARN_DB_PERMISSION_DENIED = "NOTIFICATION_RETENTION_DB_PERMISSION_DENIED";

  static final String EVT_PLAN_GENERATED = "NOTIFICATION_RETENTION_DRY_RUN_PLAN_GENERATED";
  static final String EVT_PLAN_FAILED = "NOTIFICATION_RETENTION_DRY_RUN_FAILED";
  static final String EVT_COUNT_CAPPED = "NOTIFICATION_RETENTION_COUNT_CAPPED";

  private final NotificationPlatformRetentionProperties properties;
  private final NotificationPlatformRetentionCountRepository countRepository;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;

  public NotificationPlatformRetentionPlanService(
      NotificationPlatformRetentionProperties properties,
      NotificationPlatformRetentionCountRepository countRepository,
      AuditService auditService,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.countRepository = countRepository;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
  }

  public NotificationPlatformRetentionPlanResponse buildPlan(
      Optional<NotificationPlatformRetentionTargetKey> targetFilter,
      Set<NotificationPlatformRetentionLegalHoldScope> legalHoldScopes,
      Optional<Instant> overrideGeneratedAt) {
    Instant now = overrideGeneratedAt.orElseGet(Instant::now);
    Set<NotificationPlatformRetentionLegalHoldScope> activeHolds =
        (legalHoldScopes == null || legalHoldScopes.isEmpty())
            ? EnumSet.noneOf(NotificationPlatformRetentionLegalHoldScope.class)
            : EnumSet.copyOf(legalHoldScopes);
    List<String> planWarnings = new ArrayList<>();
    List<NotificationPlatformRetentionTargetView> views = new ArrayList<>();

    if (!properties.dryRunCountsEnabled()) {
      planWarnings.add(WARN_DRY_RUN_DISABLED);
      auditService.record(EVT_PLAN_GENERATED, AGGREGATE, null, auditMetaDisabled());
      return new NotificationPlatformRetentionPlanResponse(
          SERVICE_NAME, true, now, List.of(), List.copyOf(planWarnings));
    }

    boolean fullyBlocked =
        activeHolds.stream().anyMatch(NotificationPlatformRetentionLegalHoldScope::fullyBlocking);
    boolean targetSpecificPresent = activeHolds.stream().anyMatch(scope -> !scope.fullyBlocking());

    for (NotificationPlatformRetentionTargetKey target :
        NotificationPlatformRetentionTargetKey.values()) {
      if (targetFilter.isPresent() && targetFilter.get() != target) continue;
      views.add(planTarget(target, now, fullyBlocked, activeHolds));
    }

    if (targetSpecificPresent && !fullyBlocked) {
      planWarnings.add(WARN_PARTIAL_LEGAL_HOLD_MAPPING);
    }

    Map<String, Object> meta = new HashMap<>();
    meta.put("targetCount", views.size());
    meta.put("targetFilterPresent", targetFilter.isPresent());
    meta.put("legalHoldScopeCount", activeHolds.size());
    meta.put("fullyBlocked", fullyBlocked);
    meta.put(
        "warningCount",
        views.stream().mapToInt(v -> v.warnings() == null ? 0 : v.warnings().size()).sum()
            + planWarnings.size());
    auditService.record(EVT_PLAN_GENERATED, AGGREGATE, null, meta);
    meterRegistry
        .counter("notification_retention_dry_run_total", "target", "_plan", "result", "ok")
        .increment();
    return new NotificationPlatformRetentionPlanResponse(
        SERVICE_NAME, true, now, List.copyOf(views), List.copyOf(planWarnings));
  }

  private NotificationPlatformRetentionTargetView planTarget(
      NotificationPlatformRetentionTargetKey target,
      Instant now,
      boolean fullyBlocked,
      Set<NotificationPlatformRetentionLegalHoldScope> activeHolds) {
    NotificationPlatformRetentionTargetStatus status = target.defaultStatus();
    List<String> targetWarnings = new ArrayList<>();
    boolean blocked = fullyBlocked || activeHolds.stream().anyMatch(scope -> scope.blocks(target));

    if (status == NotificationPlatformRetentionTargetStatus.INVENTORY_ONLY
        || status == NotificationPlatformRetentionTargetStatus.DISABLED) {
      targetWarnings.add(WARN_TARGET_INVENTORY_ONLY);
      if (blocked) targetWarnings.add(WARN_LEGAL_HOLD_BLOCKED);
      return new NotificationPlatformRetentionTargetView(
          target.key(), status, null, null, null, 0, blocked, List.copyOf(targetWarnings));
    }

    int retentionDays = properties.retentionDaysFor(target);
    Instant cutoff = now.minus(Duration.ofDays(retentionDays));
    Timer.Sample sample = Timer.start(meterRegistry);
    CountResult count;
    try {
      count = runCount(target, cutoff);
    } catch (RuntimeException e) {
      sample.stop(
          meterRegistry.timer(
              "notification_retention_count_duration_seconds", "target", target.key()));
      String warningCode = classifyDbFailure(e);
      auditService.record(
          EVT_PLAN_FAILED,
          AGGREGATE,
          null,
          Map.of(
              "targetKey",
              target.key(),
              "errorClass",
              e.getClass().getSimpleName(),
              "warningCode",
              warningCode));
      meterRegistry
          .counter(
              "notification_retention_dry_run_total", "target", target.key(), "result", "error")
          .increment();
      targetWarnings.add(warningCode);
      if (blocked) targetWarnings.add(WARN_LEGAL_HOLD_BLOCKED);
      return new NotificationPlatformRetentionTargetView(
          target.key(),
          status,
          retentionDays,
          cutoff,
          null,
          0,
          blocked,
          List.copyOf(targetWarnings));
    }
    sample.stop(
        meterRegistry.timer(
            "notification_retention_count_duration_seconds", "target", target.key()));

    if (count.capped()) {
      targetWarnings.add(WARN_QUERY_CAPPED);
      meterRegistry
          .counter("notification_retention_count_capped_total", "target", target.key())
          .increment();
      auditService.record(
          EVT_COUNT_CAPPED,
          AGGREGATE,
          null,
          Map.of("targetKey", target.key(), "capLimit", properties.maxCountQueryLimit()));
    }

    long purgeable = blocked ? 0 : count.count();
    meterRegistry.gauge(
        "notification_retention_eligible_count",
        List.of(Tag.of("target", target.key())),
        count.count());
    meterRegistry
        .counter("notification_retention_dry_run_total", "target", target.key(), "result", "ok")
        .increment();
    if (blocked) targetWarnings.add(WARN_LEGAL_HOLD_BLOCKED);

    return new NotificationPlatformRetentionTargetView(
        target.key(),
        status,
        retentionDays,
        cutoff,
        count.count(),
        purgeable,
        blocked,
        List.copyOf(targetWarnings));
  }

  private CountResult runCount(NotificationPlatformRetentionTargetKey target, Instant cutoff) {
    int cap = properties.maxCountQueryLimit();
    return switch (target) {
      case NOTIFICATION_ANALYTICS_HOURLY -> countRepository.countAnalyticsHourlyBefore(cutoff, cap);
      case NOTIFICATION_FANOUT_OUTBOX_SENT -> countRepository.countFanoutSentBefore(cutoff, cap);
      case NOTIFICATION_FANOUT_OUTBOX_DEAD -> countRepository.countFanoutDeadBefore(cutoff, cap);
      case NOTIFICATION_DEAD_LETTER_REQUEUE_REQUESTS ->
          countRepository.countDeadLetterRequeueBefore(cutoff, cap);
      case NOTIFICATION_DIGEST_ITEMS_TERMINAL ->
          countRepository.countDigestTerminalBefore(cutoff, cap);
      case NOTIFICATION_EMAIL_NOTIFICATIONS_TERMINAL ->
          countRepository.countEmailTerminalBefore(cutoff, cap);
    };
  }

  private Map<String, Object> auditMetaDisabled() {
    return Map.of("dryRunCountsEnabled", false, "targetCount", 0);
  }

  static String classifyDbFailure(Throwable e) {
    Throwable cause = e;
    int depth = 0;
    while (cause != null && depth < 10) {
      String className = cause.getClass().getName();
      if (className.endsWith("PermissionDeniedDataAccessException")) {
        return WARN_DB_PERMISSION_DENIED;
      }
      if (cause instanceof java.sql.SQLException sqle) {
        String state = sqle.getSQLState();
        if ("42501".equals(state)) {
          return WARN_DB_PERMISSION_DENIED;
        }
      }
      cause = cause.getCause();
      depth++;
    }
    return WARN_COUNT_FAILED;
  }
}

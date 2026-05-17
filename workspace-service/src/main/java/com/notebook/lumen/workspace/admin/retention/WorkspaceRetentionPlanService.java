package com.notebook.lumen.workspace.admin.retention;

import com.notebook.lumen.workspace.admin.retention.WorkspaceRetentionCountRepository.CountResult;
import com.notebook.lumen.workspace.admin.retention.WorkspaceRetentionPlanDtos.WorkspaceRetentionPlanResponse;
import com.notebook.lumen.workspace.admin.retention.WorkspaceRetentionPlanDtos.WorkspaceRetentionTargetView;
import com.notebook.lumen.workspace.audit.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
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
public class WorkspaceRetentionPlanService {

  static final String SERVICE_NAME = "workspace-service";
  static final String AGGREGATE_TYPE = "WORKSPACE_RETENTION";

  static final String WARN_DRY_RUN_DISABLED = "WORKSPACE_RETENTION_DRY_RUN_DISABLED";
  static final String WARN_LEGAL_HOLD_BLOCKED = "WORKSPACE_RETENTION_LEGAL_HOLD_BLOCKED";
  static final String WARN_PARTIAL_LEGAL_HOLD_MAPPING =
      "WORKSPACE_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING";
  static final String WARN_TARGET_INVENTORY_ONLY = "WORKSPACE_RETENTION_TARGET_INVENTORY_ONLY";
  static final String WARN_QUERY_CAPPED = "WORKSPACE_RETENTION_QUERY_CAPPED";
  static final String WARN_COUNT_FAILED = "WORKSPACE_RETENTION_COUNT_FAILED";
  static final String WARN_DB_PERMISSION_DENIED = "WORKSPACE_RETENTION_DB_PERMISSION_DENIED";

  static final String EVT_PLAN_GENERATED = "WORKSPACE_RETENTION_DRY_RUN_PLAN_GENERATED";
  static final String EVT_PLAN_FAILED = "WORKSPACE_RETENTION_DRY_RUN_FAILED";
  static final String EVT_COUNT_CAPPED = "WORKSPACE_RETENTION_COUNT_CAPPED";

  private final WorkspaceRetentionProperties properties;
  private final WorkspaceRetentionCountRepository countRepository;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;

  public WorkspaceRetentionPlanService(
      WorkspaceRetentionProperties properties,
      WorkspaceRetentionCountRepository countRepository,
      AuditService auditService,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.countRepository = countRepository;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
  }

  public WorkspaceRetentionPlanResponse buildPlan(
      Optional<WorkspaceRetentionTargetKey> targetFilter,
      Set<WorkspaceRetentionLegalHoldScope> legalHoldScopes,
      Optional<Instant> overrideGeneratedAt) {
    Instant now = overrideGeneratedAt.orElseGet(Instant::now);
    Set<WorkspaceRetentionLegalHoldScope> activeHolds =
        (legalHoldScopes == null || legalHoldScopes.isEmpty())
            ? EnumSet.noneOf(WorkspaceRetentionLegalHoldScope.class)
            : EnumSet.copyOf(legalHoldScopes);
    List<String> planWarnings = new ArrayList<>();
    List<WorkspaceRetentionTargetView> views = new ArrayList<>();

    if (!properties.dryRunCountsEnabled()) {
      planWarnings.add(WARN_DRY_RUN_DISABLED);
      auditService.record(
          EVT_PLAN_GENERATED, null, null, AGGREGATE_TYPE, null, auditMetaDisabled());
      return new WorkspaceRetentionPlanResponse(
          SERVICE_NAME, true, now, List.of(), List.copyOf(planWarnings));
    }

    boolean fullyBlocked =
        activeHolds.stream().anyMatch(WorkspaceRetentionLegalHoldScope::fullyBlocking);
    boolean partialMapping = activeHolds.stream().anyMatch(scope -> !scope.fullyBlocking());

    for (WorkspaceRetentionTargetKey target : WorkspaceRetentionTargetKey.values()) {
      if (targetFilter.isPresent() && targetFilter.get() != target) continue;
      views.add(planTarget(target, now, fullyBlocked, partialMapping));
    }

    if (partialMapping && !fullyBlocked) {
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
    auditService.record(EVT_PLAN_GENERATED, null, null, AGGREGATE_TYPE, null, meta);
    meterRegistry
        .counter("workspace_retention_dry_run_total", "target", "_plan", "result", "ok")
        .increment();
    return new WorkspaceRetentionPlanResponse(
        SERVICE_NAME, true, now, List.copyOf(views), List.copyOf(planWarnings));
  }

  private WorkspaceRetentionTargetView planTarget(
      WorkspaceRetentionTargetKey target,
      Instant now,
      boolean fullyBlocked,
      boolean partialMapping) {
    WorkspaceRetentionTargetStatus status = target.defaultStatus();
    List<String> targetWarnings = new ArrayList<>();

    if (status == WorkspaceRetentionTargetStatus.INVENTORY_ONLY
        || status == WorkspaceRetentionTargetStatus.DISABLED) {
      targetWarnings.add(WARN_TARGET_INVENTORY_ONLY);
      return new WorkspaceRetentionTargetView(
          target.key(),
          status,
          null,
          null,
          null,
          0,
          fullyBlocked,
          List.copyOf(addLegalHoldWarning(targetWarnings, fullyBlocked, partialMapping)));
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
              "workspace_retention_count_duration_seconds", "target", target.key()));
      String warningCode = classifyDbFailure(e);
      auditService.record(
          EVT_PLAN_FAILED,
          null,
          null,
          AGGREGATE_TYPE,
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
              "workspace_retention_dry_run_total", "target", target.key(), "result", "error")
          .increment();
      targetWarnings.add(warningCode);
      return new WorkspaceRetentionTargetView(
          target.key(),
          status,
          retentionDays,
          cutoff,
          null,
          0,
          fullyBlocked,
          List.copyOf(addLegalHoldWarning(targetWarnings, fullyBlocked, partialMapping)));
    }
    sample.stop(
        meterRegistry.timer("workspace_retention_count_duration_seconds", "target", target.key()));

    if (count.capped()) {
      targetWarnings.add(WARN_QUERY_CAPPED);
      meterRegistry
          .counter("workspace_retention_count_capped_total", "target", target.key())
          .increment();
      auditService.record(
          EVT_COUNT_CAPPED,
          null,
          null,
          AGGREGATE_TYPE,
          null,
          Map.of("targetKey", target.key(), "capLimit", properties.maxCountQueryLimit()));
    }

    long purgeable = fullyBlocked ? 0 : count.count();
    meterRegistry.gauge(
        "workspace_retention_eligible_count",
        List.of(io.micrometer.core.instrument.Tag.of("target", target.key())),
        count.count());
    meterRegistry
        .counter("workspace_retention_dry_run_total", "target", target.key(), "result", "ok")
        .increment();

    return new WorkspaceRetentionTargetView(
        target.key(),
        status,
        retentionDays,
        cutoff,
        count.count(),
        purgeable,
        fullyBlocked,
        List.copyOf(addLegalHoldWarning(targetWarnings, fullyBlocked, partialMapping)));
  }

  private List<String> addLegalHoldWarning(
      List<String> warnings, boolean fullyBlocked, boolean partialMapping) {
    if (fullyBlocked) warnings.add(WARN_LEGAL_HOLD_BLOCKED);
    if (partialMapping) warnings.add(WARN_PARTIAL_LEGAL_HOLD_MAPPING);
    return warnings;
  }

  private CountResult runCount(WorkspaceRetentionTargetKey target, Instant cutoff) {
    int cap = properties.maxCountQueryLimit();
    return switch (target) {
      case WORKSPACE_INVITATIONS_EXPIRED ->
          countRepository.countExpiredPendingInvitationsBefore(cutoff, cap);
      case WORKSPACE_AUDIT_LIKE_EVENTS -> countRepository.countAuditEventsBefore(cutoff, cap);
      default -> new CountResult(0, false);
    };
  }

  private static Map<String, Object> auditMetaDisabled() {
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

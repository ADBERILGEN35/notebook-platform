package com.notebook.lumen.content.admin.retention;

import com.notebook.lumen.content.admin.retention.ContentRetentionCountRepository.CountResult;
import com.notebook.lumen.content.admin.retention.ContentRetentionPlanDtos.ContentRetentionPlanResponse;
import com.notebook.lumen.content.admin.retention.ContentRetentionPlanDtos.ContentRetentionTargetView;
import com.notebook.lumen.content.audit.AuditService;
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
public class ContentRetentionPlanService {

  static final String SERVICE_NAME = "content-service";
  static final String AGGREGATE = "CONTENT_RETENTION";

  static final String WARN_DRY_RUN_DISABLED = "CONTENT_RETENTION_DRY_RUN_DISABLED";
  static final String WARN_LEGAL_HOLD_BLOCKED = "CONTENT_RETENTION_LEGAL_HOLD_BLOCKED";
  static final String WARN_PARTIAL_LEGAL_HOLD_MAPPING =
      "CONTENT_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING";
  static final String WARN_TARGET_INVENTORY_ONLY = "CONTENT_RETENTION_TARGET_INVENTORY_ONLY";
  static final String WARN_QUERY_CAPPED = "CONTENT_RETENTION_QUERY_CAPPED";
  static final String WARN_COUNT_FAILED = "CONTENT_RETENTION_COUNT_FAILED";
  static final String WARN_DB_PERMISSION_DENIED = "CONTENT_RETENTION_DB_PERMISSION_DENIED";
  static final String WARN_RLS_NOT_READY = "CONTENT_RETENTION_RLS_NOT_READY";

  static final String EVT_PLAN_GENERATED = "CONTENT_RETENTION_DRY_RUN_PLAN_GENERATED";
  static final String EVT_PLAN_FAILED = "CONTENT_RETENTION_DRY_RUN_FAILED";
  static final String EVT_COUNT_CAPPED = "CONTENT_RETENTION_COUNT_CAPPED";

  private final ContentRetentionProperties properties;
  private final ContentRetentionCountRepository countRepository;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;

  public ContentRetentionPlanService(
      ContentRetentionProperties properties,
      ContentRetentionCountRepository countRepository,
      AuditService auditService,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.countRepository = countRepository;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
  }

  public ContentRetentionPlanResponse buildPlan(
      Optional<ContentRetentionTargetKey> targetFilter,
      Set<ContentRetentionLegalHoldScope> legalHoldScopes,
      Optional<Instant> overrideGeneratedAt) {
    Instant now = overrideGeneratedAt.orElseGet(Instant::now);
    Set<ContentRetentionLegalHoldScope> activeHolds =
        (legalHoldScopes == null || legalHoldScopes.isEmpty())
            ? EnumSet.noneOf(ContentRetentionLegalHoldScope.class)
            : EnumSet.copyOf(legalHoldScopes);
    List<String> planWarnings = new ArrayList<>();
    List<ContentRetentionTargetView> views = new ArrayList<>();

    if (!properties.dryRunEnabled()) {
      planWarnings.add(WARN_DRY_RUN_DISABLED);
      ContentRetentionPlanResponse disabled =
          new ContentRetentionPlanResponse(SERVICE_NAME, true, now, List.of(), planWarnings);
      auditService.record(EVT_PLAN_GENERATED, null, null, AGGREGATE, null, auditMetaDisabled());
      return disabled;
    }

    boolean partialMapping = activeHolds.stream().anyMatch(scope -> !scope.fullyBlocking());
    boolean fullyBlocked =
        activeHolds.stream().anyMatch(ContentRetentionLegalHoldScope::fullyBlocking);

    for (ContentRetentionTargetKey target : ContentRetentionTargetKey.values()) {
      if (targetFilter.isPresent() && targetFilter.get() != target) continue;
      views.add(planTarget(target, now, fullyBlocked, partialMapping));
    }

    if (partialMapping) {
      planWarnings.add(WARN_PARTIAL_LEGAL_HOLD_MAPPING);
    }

    Map<String, Object> meta = new HashMap<>();
    meta.put("targetCount", views.size());
    meta.put("targetFilterPresent", targetFilter.isPresent());
    meta.put("legalHoldScopeCount", activeHolds.size());
    meta.put("fullyBlocked", fullyBlocked);
    meta.put("partialMapping", partialMapping);
    meta.put(
        "warningCount",
        views.stream().mapToInt(v -> v.warnings() == null ? 0 : v.warnings().size()).sum()
            + planWarnings.size());
    auditService.record(EVT_PLAN_GENERATED, null, null, AGGREGATE, null, meta);
    meterRegistry
        .counter("content_retention_dry_run_total", "target", "_plan", "result", "ok")
        .increment();
    return new ContentRetentionPlanResponse(
        SERVICE_NAME, true, now, List.copyOf(views), List.copyOf(planWarnings));
  }

  private ContentRetentionTargetView planTarget(
      ContentRetentionTargetKey target, Instant now, boolean fullyBlocked, boolean partialMapping) {
    ContentRetentionTargetStatus status = target.defaultStatus();
    List<String> targetWarnings = new ArrayList<>();

    if (status == ContentRetentionTargetStatus.INVENTORY_ONLY) {
      targetWarnings.add(WARN_TARGET_INVENTORY_ONLY);
      return new ContentRetentionTargetView(
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
          meterRegistry.timer("content_retention_count_duration_seconds", "target", target.key()));
      String warningCode = classifyDbFailure(e);
      auditService.record(
          EVT_PLAN_FAILED,
          null,
          null,
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
          .counter("content_retention_dry_run_total", "target", target.key(), "result", "error")
          .increment();
      targetWarnings.add(warningCode);
      return new ContentRetentionTargetView(
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
        meterRegistry.timer("content_retention_count_duration_seconds", "target", target.key()));

    if (count.capped()) {
      targetWarnings.add(WARN_QUERY_CAPPED);
      meterRegistry
          .counter("content_retention_count_capped_total", "target", target.key())
          .increment();
      auditService.record(
          EVT_COUNT_CAPPED,
          null,
          null,
          AGGREGATE,
          null,
          Map.of("targetKey", target.key(), "capLimit", properties.maxCountQueryLimit()));
    }

    long purgeable = fullyBlocked ? 0 : count.count();
    meterRegistry.gauge(
        "content_retention_eligible_count",
        List.of(io.micrometer.core.instrument.Tag.of("target", target.key())),
        count.count());
    meterRegistry
        .counter("content_retention_dry_run_total", "target", target.key(), "result", "ok")
        .increment();

    return new ContentRetentionTargetView(
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

  private CountResult runCount(ContentRetentionTargetKey target, Instant cutoff) {
    int cap = properties.maxCountQueryLimit();
    return switch (target) {
      case CONTENT_NOTE_VERSIONS -> countRepository.countNoteVersionsBefore(cutoff, cap);
      case CONTENT_COMMENTS -> countRepository.countCommentsBefore(cutoff, cap);
      case CONTENT_SEARCH_DOCUMENTS -> countRepository.countSearchDocumentsBefore(cutoff, cap);
      case CONTENT_NOTES -> new CountResult(0, false);
    };
  }

  private Map<String, Object> auditMetaDisabled() {
    return Map.of("dryRunEnabled", false, "targetCount", 0);
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

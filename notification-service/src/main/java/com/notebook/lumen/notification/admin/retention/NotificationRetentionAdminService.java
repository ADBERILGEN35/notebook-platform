package com.notebook.lumen.notification.admin.retention;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class NotificationRetentionAdminService {

  private static final String AGGREGATE = "NOTIFICATION_RETENTION";

  private final NotificationRetentionProperties retentionProperties;
  private final NotificationRetentionPlanner planner;
  private final NotificationRetentionPurgeExecutor purgeExecutor;
  private final NotificationRetentionMetrics metrics;
  private final AuditService auditService;

  public NotificationRetentionAdminService(
      NotificationRetentionProperties retentionProperties,
      NotificationRetentionPlanner planner,
      NotificationRetentionPurgeExecutor purgeExecutor,
      NotificationRetentionMetrics metrics,
      AuditService auditService) {
    this.retentionProperties = retentionProperties;
    this.planner = planner;
    this.purgeExecutor = purgeExecutor;
    this.metrics = metrics;
    this.auditService = auditService;
  }

  public RetentionAdminDtos.RetentionPlanResponse plan(Instant now, boolean dryRun) {
    var body = planner.plan(now, dryRun);
    long eligible =
        body.targets().stream()
            .mapToLong(RetentionAdminDtos.RetentionPlanTarget::eligibleCount)
            .sum();
    long blockedTargets =
        body.targets().stream()
            .filter(RetentionAdminDtos.RetentionPlanTarget::blockedByLegalHold)
            .count();
    auditService.record(
        RetentionAuditEventType.PLAN_VIEWED,
        AGGREGATE,
        UUID.randomUUID(),
        Map.of(
            "dryRun",
            dryRun,
            "eligibleTotal",
            eligible,
            "targetCount",
            body.targets().size(),
            "blockedByLegalHoldTargets",
            blockedTargets));
    return body;
  }

  public RetentionAdminDtos.RetentionRunResponse run(
      boolean dryRun, String targetRaw, String reason, String actorUserId) {
    Instant now = Instant.now();
    Set<RetentionPurgeKind> kinds;
    try {
      kinds = RetentionPurgeKind.parseTargets(targetRaw == null ? "ALL" : targetRaw);
    } catch (IllegalArgumentException ex) {
      if (!dryRun && actorUserId != null && !actorUserId.isBlank()) {
        auditService.record(
            RetentionAuditEventType.PURGE_DENIED,
            AGGREGATE,
            UUID.randomUUID(),
            Map.of("code", "BAD_TARGET", "actorUserId", actorUserId));
      }
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "RETENTION_BAD_TARGET", ex.getMessage());
    }

    var planBefore = planner.plan(now, dryRun);
    long skippedByLegalHold = skippedEligibleForKinds(planBefore, kinds);
    List<String> legalHoldKeysBlocking = blockingHoldKeysForKinds(planBefore, kinds);

    if (dryRun) {
      auditService.record(
          RetentionAuditEventType.DRY_RUN,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of(
              "target",
              targetRaw == null ? "ALL" : targetRaw,
              "reasonPresent",
              reason != null && !reason.isBlank(),
              "actorPresent",
              actorUserId != null && !actorUserId.isBlank(),
              "skippedByLegalHold",
              skippedByLegalHold));
      return new RetentionAdminDtos.RetentionRunResponse(
          true,
          targetRaw == null || targetRaw.isBlank() ? "ALL" : targetRaw,
          0,
          Map.of(),
          skippedByLegalHold,
          legalHoldKeysBlocking,
          planBefore);
    }

    validateDestructive(reason, actorUserId);
    if (!retentionProperties.manualRunEnabled()) {
      auditService.record(
          RetentionAuditEventType.PURGE_DENIED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of("code", "MANUAL_RUN_DISABLED", "actorUserId", actorUserId));
      throw new NotificationException(
          HttpStatus.FORBIDDEN,
          "RETENTION_MANUAL_RUN_DISABLED",
          "Manual retention purge is disabled for this environment.");
    }

    auditService.record(
        RetentionAuditEventType.PURGE_STARTED,
        AGGREGATE,
        UUID.randomUUID(),
        Map.of(
            "target",
            targetRaw == null || targetRaw.isBlank() ? "ALL" : targetRaw,
            "reasonPresent",
            true,
            "actorUserId",
            actorUserId,
            "maxDeletePerRun",
            retentionProperties.maxDeletePerRun(),
            "skippedByLegalHoldEligible",
            skippedByLegalHold));

    try {
      Map<String, Long> deleted =
          purgeExecutor.purge(kinds, now, retentionProperties.maxDeletePerRun());
      long total = deleted.values().stream().mapToLong(Long::longValue).sum();
      auditService.record(
          RetentionAuditEventType.PURGE_COMPLETED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of(
              "actorUserId",
              actorUserId,
              "totalDeleted",
              total,
              "skippedByLegalHoldEligible",
              skippedByLegalHold,
              "legalHoldKeysBlocking",
              String.join(",", legalHoldKeysBlocking),
              "perTarget",
              deleted.entrySet().stream()
                  .collect(
                      Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())))));
      var afterPlan = planner.plan(Instant.now(), false);
      return new RetentionAdminDtos.RetentionRunResponse(
          false,
          targetRaw == null || targetRaw.isBlank() ? "ALL" : targetRaw,
          total,
          new HashMap<>(deleted),
          skippedByLegalHold,
          legalHoldKeysBlocking,
          afterPlan);
    } catch (RuntimeException ex) {
      auditService.record(
          RetentionAuditEventType.PURGE_FAILED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of("actorUserId", actorUserId, "error", ex.getClass().getSimpleName()));
      throw ex;
    }
  }

  public void workerTick() {
    Instant now = Instant.now();
    if (!retentionProperties.workerEnabled()) {
      return;
    }
    if (retentionProperties.dryRunOnly()) {
      planner.plan(now, true);
      metrics.recordWorkerRun(now, "dry_run_only");
      return;
    }
    try {
      Set<RetentionPurgeKind> all = RetentionPurgeKind.parseTargets("ALL");
      purgeExecutor.purge(all, now, retentionProperties.maxDeletePerRun());
      auditService.record(
          RetentionAuditEventType.WORKER_PURGE_COMPLETED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of("maxDeletePerRun", retentionProperties.maxDeletePerRun()));
      metrics.recordWorkerRun(now, "success");
    } catch (RuntimeException ex) {
      auditService.record(
          RetentionAuditEventType.WORKER_PURGE_FAILED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of("error", ex.getClass().getSimpleName()));
      metrics.recordWorkerRun(now, "failure");
    }
  }

  private static long skippedEligibleForKinds(
      RetentionAdminDtos.RetentionPlanResponse plan, Set<RetentionPurgeKind> kinds) {
    long sum = 0;
    for (RetentionAdminDtos.RetentionPlanTarget t : plan.targets()) {
      var ok = RetentionPurgeKind.tryFromApiTargetKey(t.target());
      if (ok.isEmpty()) {
        continue;
      }
      if (kinds.contains(ok.get()) && t.blockedByLegalHold()) {
        sum += t.eligibleCount();
      }
    }
    return sum;
  }

  private static List<String> blockingHoldKeysForKinds(
      RetentionAdminDtos.RetentionPlanResponse plan, Set<RetentionPurgeKind> kinds) {
    LinkedHashSet<String> keys = new LinkedHashSet<>();
    for (RetentionAdminDtos.RetentionPlanTarget t : plan.targets()) {
      var ok = RetentionPurgeKind.tryFromApiTargetKey(t.target());
      if (ok.isEmpty()) {
        continue;
      }
      if (kinds.contains(ok.get()) && t.blockedByLegalHold()) {
        keys.addAll(t.activeHoldKeys());
      }
    }
    return new ArrayList<>(keys);
  }

  private void validateDestructive(String reason, String actorUserId) {
    if (actorUserId == null || actorUserId.isBlank()) {
      auditService.record(
          RetentionAuditEventType.PURGE_DENIED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of("code", "ACTOR_REQUIRED"));
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "RETENTION_ACTOR_REQUIRED", "Admin actor header is required");
    }
    if (reason == null || reason.trim().length() < 10) {
      auditService.record(
          RetentionAuditEventType.PURGE_DENIED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of("code", "REASON_REQUIRED", "actorUserId", actorUserId));
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "RETENTION_REASON_REQUIRED",
          "Reason must be at least 10 characters.");
    }
  }
}

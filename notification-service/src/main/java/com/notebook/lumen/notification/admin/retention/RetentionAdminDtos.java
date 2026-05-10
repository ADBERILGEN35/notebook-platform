package com.notebook.lumen.notification.admin.retention;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RetentionAdminDtos {

  private RetentionAdminDtos() {}

  public record RetentionPlanResponse(
      Instant generatedAt,
      boolean dryRun,
      List<RetentionPlanTarget> targets,
      List<String> warnings) {}

  public record RetentionPlanTarget(
      String target,
      long eligibleCount,
      String retention,
      Instant oldestEligibleAt,
      Instant cutoff,
      boolean blockedByLegalHold,
      List<String> activeHoldKeys,
      long purgeableCount,
      List<String> targetWarnings) {}

  /**
   * {@code dryRun} null defaults to true (safe) when JSON omits the field.
   */
  public record RetentionRunRequest(Boolean dryRun, String target, String reason) {
    public boolean effectiveDryRun() {
      return dryRun == null || dryRun;
    }
  }

  public record RetentionRunResponse(
      boolean dryRun,
      String target,
      long totalDeleted,
      Map<String, Long> deletedByTarget,
      long skippedByLegalHold,
      List<String> legalHoldKeysBlocking,
      RetentionPlanResponse planSnapshot) {}
}

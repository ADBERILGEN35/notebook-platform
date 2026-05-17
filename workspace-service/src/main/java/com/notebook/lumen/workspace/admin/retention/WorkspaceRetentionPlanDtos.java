package com.notebook.lumen.workspace.admin.retention;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public final class WorkspaceRetentionPlanDtos {
  private WorkspaceRetentionPlanDtos() {}

  public record WorkspaceRetentionPlanResponse(
      String service,
      boolean dryRun,
      Instant generatedAt,
      List<WorkspaceRetentionTargetView> targets,
      List<String> warnings) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record WorkspaceRetentionTargetView(
      String targetKey,
      WorkspaceRetentionTargetStatus status,
      Integer defaultRetentionDays,
      Instant cutoff,
      Long eligibleCount,
      long purgeableCount,
      boolean blockedByLegalHold,
      List<String> warnings) {}
}

package com.notebook.lumen.content.admin.retention;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public final class ContentRetentionPlanDtos {
  private ContentRetentionPlanDtos() {}

  public record ContentRetentionPlanResponse(
      String service,
      boolean dryRun,
      Instant generatedAt,
      List<ContentRetentionTargetView> targets,
      List<String> warnings) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ContentRetentionTargetView(
      String targetKey,
      ContentRetentionTargetStatus status,
      Integer defaultRetentionDays,
      Instant cutoff,
      Long eligibleCount,
      long purgeableCount,
      boolean blockedByLegalHold,
      List<String> warnings) {}
}

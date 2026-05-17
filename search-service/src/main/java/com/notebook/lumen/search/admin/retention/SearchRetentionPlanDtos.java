package com.notebook.lumen.search.admin.retention;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public final class SearchRetentionPlanDtos {
  private SearchRetentionPlanDtos() {}

  public record SearchRetentionPlanResponse(
      String service,
      boolean dryRun,
      Instant generatedAt,
      List<SearchRetentionTargetView> targets,
      List<String> warnings) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SearchRetentionTargetView(
      String targetKey,
      SearchRetentionTargetStatus status,
      Integer defaultRetentionDays,
      Instant cutoff,
      Long eligibleCount,
      long purgeableCount,
      boolean blockedByLegalHold,
      List<String> warnings) {}
}

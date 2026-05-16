package com.notebook.lumen.notification.admin.platformretention;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public final class NotificationPlatformRetentionDtos {
  private NotificationPlatformRetentionDtos() {}

  public record NotificationPlatformRetentionPlanResponse(
      String service,
      boolean dryRun,
      Instant generatedAt,
      List<NotificationPlatformRetentionTargetView> targets,
      List<String> warnings) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record NotificationPlatformRetentionTargetView(
      String targetKey,
      NotificationPlatformRetentionTargetStatus status,
      Integer defaultRetentionDays,
      Instant cutoff,
      Long eligibleCount,
      long purgeableCount,
      boolean blockedByLegalHold,
      List<String> warnings) {}
}

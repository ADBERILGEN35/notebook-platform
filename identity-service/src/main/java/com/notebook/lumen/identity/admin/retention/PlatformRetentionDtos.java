package com.notebook.lumen.identity.admin.retention;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlatformRetentionDtos {
  private PlatformRetentionDtos() {}

  public record TargetsResponse(Instant generatedAt, List<TargetResponse> targets) {}

  public record TargetResponse(
      String targetKey,
      String service,
      String displayName,
      String description,
      RetentionDataClass dataClass,
      Integer defaultRetentionDays,
      boolean legalHoldSupported,
      boolean destructivePurgeSupported,
      boolean dryRunSupported,
      boolean archiveRequiredBeforePurge,
      RetentionRiskLevel riskLevel,
      RetentionTargetStatus status) {}

  public record PlanResponse(
      Instant generatedAt,
      boolean dryRun,
      List<PlanTargetResponse> targets,
      List<String> warnings) {}

  public record PlanTargetResponse(
      String targetKey,
      String service,
      RetentionTargetStatus status,
      Integer defaultRetentionDays,
      Long eligibleCount,
      long purgeableCount,
      boolean blockedByLegalHold,
      RetentionRiskLevel riskLevel,
      List<String> activeHoldKeys,
      List<String> warnings) {}

  public record LegalHoldListResponse(List<LegalHoldResponse> items) {}

  public record LegalHoldResponse(
      UUID id,
      String holdKey,
      PlatformLegalHoldScope scope,
      boolean scopeRefPresent,
      PlatformLegalHoldStatus status,
      UUID createdByUserId,
      Instant createdAt,
      UUID releasedByUserId,
      Instant releasedAt,
      Instant expiresAt) {}

  public record LegalHoldCreateRequest(
      String holdKey,
      PlatformLegalHoldScope scope,
      UUID scopeRefId,
      String reason,
      Instant expiresAt) {}

  public record LegalHoldReleaseRequest(String reason) {}
}

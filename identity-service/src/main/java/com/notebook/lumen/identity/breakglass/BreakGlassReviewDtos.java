package com.notebook.lumen.identity.breakglass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BreakGlassReviewDtos {
  private BreakGlassReviewDtos() {}

  public record EventItem(
      UUID id,
      String sessionId,
      String mode,
      String actorLabel,
      String status,
      String tokenStatus,
      boolean rotationRequired,
      Instant issuedAt,
      Instant expiresAt,
      Instant reviewedAt) {}

  public record EventListResponse(List<EventItem> items, long total, long overdueCount) {}

  public record EventDetailResponse(
      UUID id,
      String sessionId,
      String mode,
      String actorLabel,
      String status,
      String tokenStatus,
      boolean rotationRequired,
      Instant issuedAt,
      Instant expiresAt,
      String reasonSummary,
      Instant reviewedAt,
      UUID reviewedByUserId,
      String reviewDecision,
      String reviewReason,
      Instant tokenRevokedAt) {}

  public record ReviewRequest(
      @NotBlank @Size(min = 3, max = 20) String decision,
      @NotBlank @Size(min = 10, max = 2000) String reason,
      boolean rotationRunbookAcknowledged) {}

  public record RevokeTokenRequest(@NotBlank @Size(min = 10, max = 2000) String reason) {}

  public record RevokeTokenResponse(
      boolean revoked, boolean alreadyExpired, boolean alreadyRevoked) {}

  public record TokenRevokedStatusResponse(
      boolean revoked, String sessionId, Instant expiresAt, String source) {}
}

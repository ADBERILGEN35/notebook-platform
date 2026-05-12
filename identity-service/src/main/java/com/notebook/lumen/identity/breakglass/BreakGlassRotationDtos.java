package com.notebook.lumen.identity.breakglass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BreakGlassRotationDtos {
  private BreakGlassRotationDtos() {}

  /** Detail surface. Never exposes raw token or full hash — only short fingerprint prefixes. */
  public record RotationEventItem(
      UUID id,
      String credentialMode,
      String status,
      String oldFingerprint,
      String newFingerprint,
      UUID triggeredByEventId,
      String triggeredBySessionId,
      Instant requiredAt,
      Instant acknowledgedAt,
      Instant verifiedAt,
      Instant closedAt) {}

  public record RotationEventDetail(
      UUID id,
      String credentialMode,
      String status,
      String oldFingerprint,
      String newFingerprint,
      UUID triggeredByEventId,
      String triggeredBySessionId,
      Instant requiredAt,
      Instant acknowledgedAt,
      UUID acknowledgedByUserId,
      Instant verifiedAt,
      UUID verifiedByUserId,
      Instant closedAt,
      String reason) {}

  public record RotationEventListResponse(
      List<RotationEventItem> items, long total, long openCount, long requiredCount) {}

  public record RotationSummary(
      boolean trackingEnabled,
      boolean rotationRequired,
      long openRotationEvents,
      Instant oldestRequiredAt,
      Instant lastRotationVerifiedAt) {}

  public record AcknowledgeRequest(@NotBlank @Size(min = 10, max = 2000) String reason) {}

  public record VerifyRequest(@NotBlank @Size(min = 10, max = 2000) String reason) {}

  public record CloseRequest(@NotBlank @Size(min = 10, max = 2000) String reason) {}
}

package com.notebook.lumen.identity.breakglass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BreakGlassSessionDtos {
  private BreakGlassSessionDtos() {}

  public record SessionItem(
      UUID eventId,
      String sessionId,
      String revokeRef,
      String jtiMasked,
      String mode,
      String actorLabel,
      String status,
      String tokenStatus,
      Instant issuedAt,
      Instant expiresAt,
      String revocationSource) {}

  public record SessionListResponse(List<SessionItem> items, long activeCount) {}

  public record RevokeSessionRequest(@NotBlank @Size(min = 10, max = 2000) String reason) {}

  public record RevokeSessionResponse(
      boolean revoked, boolean alreadyExpired, boolean alreadyRevoked, String sessionId) {}

  public record RevokeAllActiveRequest(@NotBlank @Size(min = 10, max = 2000) String reason) {}

  public record RevokeAllActiveResponse(
      int revokedCount, int alreadyRevokedCount, int expiredCount) {}
}

package com.notebook.lumen.identity.breakglass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class BreakGlassDtos {
  private BreakGlassDtos() {}

  public record BreakGlassLoginRequest(
      @NotBlank @Size(min = 1, max = 4096) String token,
      @NotBlank @Size(min = 20, max = 2000) String reason) {}

  public record BreakGlassOfflineSignedLoginRequest(
      @NotBlank @Size(min = 1, max = 10000) String assertion,
      @NotBlank @Size(min = 20, max = 2000) String reason) {}

  public record BreakGlassWebauthnChallengeRequest(@NotBlank @Size(min = 20, max = 2000) String reason) {}

  public record BreakGlassWebauthnChallengeResponse(String challengeId, String challenge) {}

  public record BreakGlassWebauthnVerifyRequest(
      @NotBlank @Size(min = 1, max = 2000) String challengeId,
      @NotBlank @Size(min = 1, max = 10000) String credential,
      @NotBlank @Size(min = 20, max = 2000) String reason) {}

  public record BreakGlassLoginResponse(
      String accessToken, String tokenType, long expiresIn, boolean breakGlass) {}

  public record BreakGlassStatusResponse(
      boolean enabled,
      String credentialMode,
      java.util.List<String> allowedModes,
      int sessionTtlMinutes,
      int maxActiveSessions,
      boolean requireReason,
      boolean requireMfa,
      boolean staticTokenConfigured,
      boolean webauthnEnabled,
      int webauthnCredentialCount,
      boolean offlineSignedEnabled,
      boolean offlinePublicKeyConfigured,
      boolean staticTokenRotationRecommended,
      java.time.Instant lastStaticTokenUsedAt,
      String approvalMode,
      long pendingReviewCount,
      long overdueReviewCount) {}
}


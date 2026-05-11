package com.notebook.lumen.identity.breakglass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class BreakGlassDtos {
  private BreakGlassDtos() {}

  public record BreakGlassLoginRequest(
      @NotBlank @Size(min = 1, max = 4096) String token,
      @NotBlank @Size(min = 20, max = 2000) String reason) {}

  public record BreakGlassLoginResponse(
      String accessToken, String tokenType, long expiresIn, boolean breakGlass) {}

  public record BreakGlassStatusResponse(
      boolean enabled,
      int sessionTtlMinutes,
      int maxActiveSessions,
      boolean requireReason,
      boolean requireMfa,
      boolean tokenConfigured) {}
}


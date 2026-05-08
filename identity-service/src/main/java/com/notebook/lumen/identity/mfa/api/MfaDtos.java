package com.notebook.lumen.identity.mfa.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public class MfaDtos {
  public record MfaSettingsResponse(
      boolean mfaEnabled,
      boolean webauthnEnabled,
      boolean backupCodesEnabled,
      int recoveryCodesRemaining,
      int activeCredentialCount,
      boolean mfaRequired,
      List<String> availableMethods) {}

  public record WebAuthnRegistrationOptionsResponse(
      String challenge,
      String rpId,
      String rpName,
      String userId,
      String userName,
      String userDisplayName,
      List<Map<String, String>> excludeCredentials,
      String userVerification) {}

  public record WebAuthnRegistrationVerifyRequest(
      @NotBlank String credentialId,
      @NotBlank String publicKeyCose,
      @NotBlank String challenge,
      @NotBlank String origin,
      Long signCount,
      String name) {}

  public record WebAuthnAuthenticationOptionsRequest(@NotBlank String mfaSessionId) {}

  public record WebAuthnAuthenticationOptionsResponse(
      String mfaSessionId, String challenge, String rpId, List<String> allowCredentialIds, String userVerification) {}

  public record WebAuthnAuthenticationVerifyRequest(
      @NotBlank String mfaSessionId,
      @NotBlank String credentialId,
      @NotBlank String challenge,
      @NotBlank String origin,
      Long signCount) {}

  public record RecoveryCodeGenerateResponse(List<String> codes) {}

  public record RecoveryCodeGenerateRequest(boolean acknowledgeReplace) {}

  public record RecoveryCodeVerifyRequest(@NotBlank String mfaSessionId, @NotBlank String recoveryCode) {}

  public record MfaCredentialResponse(
      String credentialId, String name, String createdAt, String lastUsedAt, String revokedAt) {}

  public record CredentialRenameRequest(@NotBlank String name) {}
}

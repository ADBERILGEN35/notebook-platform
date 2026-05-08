package com.notebook.lumen.identity.mfa.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class MfaDtos {
  public record MfaSettingsResponse(
      boolean mfaEnabled,
      boolean webauthnEnabled,
      boolean backupCodesEnabled,
      boolean mfaRequired,
      List<String> availableMethods) {}

  public record WebAuthnOptionsRequest(String mfaToken) {}

  public record WebAuthnOptionsResponse(String challenge, String rpId, String userVerification) {}

  public record WebAuthnVerifyRequest(@NotBlank String mfaToken, @NotBlank String credentialJson) {}

  public record RecoveryCodeGenerateResponse(List<String> codes) {}

  public record RecoveryCodeVerifyRequest(@NotBlank String mfaToken, @NotBlank String recoveryCode) {}

  public record MfaCredentialResponse(String credentialId, String name, String createdAt, String lastUsedAt) {}
}

package com.notebook.lumen.identity.mfa.api;

import com.notebook.lumen.identity.mfa.MfaProperties;
import com.notebook.lumen.identity.mfa.api.MfaDtos.MfaCredentialResponse;
import com.notebook.lumen.identity.mfa.api.MfaDtos.MfaSettingsResponse;
import com.notebook.lumen.identity.mfa.api.MfaDtos.RecoveryCodeGenerateResponse;
import com.notebook.lumen.identity.mfa.api.MfaDtos.RecoveryCodeVerifyRequest;
import com.notebook.lumen.identity.mfa.api.MfaDtos.WebAuthnOptionsRequest;
import com.notebook.lumen.identity.mfa.api.MfaDtos.WebAuthnOptionsResponse;
import com.notebook.lumen.identity.mfa.api.MfaDtos.WebAuthnVerifyRequest;
import com.notebook.lumen.identity.shared.exception.MfaNotEnabledException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/mfa")
public class MfaController {
  private final MfaProperties properties;

  public MfaController(MfaProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/settings")
  public MfaSettingsResponse settings(@AuthenticationPrincipal Jwt accessToken) {
    UUID userId = requireUserId(accessToken);
    if (!properties.enabled()) {
      return new MfaSettingsResponse(false, false, false, false, List.of());
    }
    return new MfaSettingsResponse(true, properties.webauthnEnabled(), false, false, List.of("WEBAUTHN", "RECOVERY_CODE"));
  }

  @PostMapping("/webauthn/registration/options")
  public WebAuthnOptionsResponse registrationOptions(
      @AuthenticationPrincipal Jwt accessToken, @RequestBody(required = false) WebAuthnOptionsRequest request) {
    requireUserId(accessToken);
    requireWebauthnEnabled();
    return new WebAuthnOptionsResponse("challenge-placeholder", "localhost", "preferred");
  }

  @PostMapping("/webauthn/registration/verify")
  public ResponseEntity<Void> registrationVerify(
      @AuthenticationPrincipal Jwt accessToken, @Valid @RequestBody WebAuthnVerifyRequest request) {
    requireUserId(accessToken);
    requireWebauthnEnabled();
    return ResponseEntity.notFound().build();
  }

  @PostMapping("/webauthn/authentication/options")
  public WebAuthnOptionsResponse authenticationOptions(@Valid @RequestBody WebAuthnOptionsRequest request) {
    requireWebauthnEnabled();
    return new WebAuthnOptionsResponse("challenge-placeholder", "localhost", "preferred");
  }

  @PostMapping("/webauthn/authentication/verify")
  public ResponseEntity<Void> authenticationVerify(@Valid @RequestBody WebAuthnVerifyRequest request) {
    requireWebauthnEnabled();
    return ResponseEntity.notFound().build();
  }

  @PostMapping("/recovery-codes/generate")
  public RecoveryCodeGenerateResponse generateRecoveryCodes(@AuthenticationPrincipal Jwt accessToken) {
    requireUserId(accessToken);
    requireMfaEnabled();
    return new RecoveryCodeGenerateResponse(List.of());
  }

  @PostMapping("/recovery-codes/verify")
  public ResponseEntity<Void> verifyRecoveryCode(@Valid @RequestBody RecoveryCodeVerifyRequest request) {
    requireMfaEnabled();
    return ResponseEntity.notFound().build();
  }

  @GetMapping("/webauthn/credentials")
  public List<MfaCredentialResponse> credentials(@AuthenticationPrincipal Jwt accessToken) {
    requireUserId(accessToken);
    requireWebauthnEnabled();
    return List.of();
  }

  @PatchMapping("/webauthn/credentials/{credentialId}")
  public ResponseEntity<Void> updateCredential(
      @AuthenticationPrincipal Jwt accessToken, @PathVariable String credentialId) {
    requireUserId(accessToken);
    requireWebauthnEnabled();
    return ResponseEntity.notFound().build();
  }

  @DeleteMapping("/webauthn/credentials/{credentialId}")
  public ResponseEntity<Void> deleteCredential(
      @AuthenticationPrincipal Jwt accessToken, @PathVariable String credentialId) {
    requireUserId(accessToken);
    requireWebauthnEnabled();
    return ResponseEntity.notFound().build();
  }

  private void requireMfaEnabled() {
    if (!properties.enabled()) {
      throw new MfaNotEnabledException("MFA endpoints are not enabled");
    }
  }

  private void requireWebauthnEnabled() {
    requireMfaEnabled();
    if (!properties.webauthnEnabled()) {
      throw new MfaNotEnabledException("WebAuthn endpoints are not enabled");
    }
  }

  private UUID requireUserId(Jwt accessToken) {
    if (accessToken == null) {
      throw new MfaNotEnabledException("Authenticated user context is required");
    }
    return UUID.fromString(accessToken.getSubject());
  }
}

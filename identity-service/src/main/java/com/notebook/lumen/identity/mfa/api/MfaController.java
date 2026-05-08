package com.notebook.lumen.identity.mfa.api;

import com.notebook.lumen.identity.auth.api.AuthResponse;
import com.notebook.lumen.identity.auth.application.AuthCookieService;
import com.notebook.lumen.identity.auth.application.AuthService;
import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.mfa.MfaProperties;
import com.notebook.lumen.identity.mfa.application.MfaService;
import com.notebook.lumen.identity.mfa.api.MfaDtos.MfaCredentialResponse;
import com.notebook.lumen.identity.mfa.api.MfaDtos.CredentialRenameRequest;
import com.notebook.lumen.identity.mfa.api.MfaDtos.MfaSettingsResponse;
import com.notebook.lumen.identity.mfa.api.MfaDtos.RecoveryCodeGenerateRequest;
import com.notebook.lumen.identity.mfa.api.MfaDtos.RecoveryCodeGenerateResponse;
import com.notebook.lumen.identity.mfa.api.MfaDtos.RecoveryCodeVerifyRequest;
import com.notebook.lumen.identity.mfa.api.MfaDtos.WebAuthnAuthenticationOptionsRequest;
import com.notebook.lumen.identity.mfa.api.MfaDtos.WebAuthnAuthenticationOptionsResponse;
import com.notebook.lumen.identity.mfa.api.MfaDtos.WebAuthnAuthenticationVerifyRequest;
import com.notebook.lumen.identity.mfa.api.MfaDtos.WebAuthnRegistrationOptionsResponse;
import com.notebook.lumen.identity.mfa.api.MfaDtos.WebAuthnRegistrationVerifyRequest;
import com.notebook.lumen.identity.shared.exception.MfaException;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
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
  private final MfaService mfaService;
  private final AuthService authService;
  private final AuthCookieService authCookieService;
  private final AuditService auditService;
  private final com.notebook.lumen.identity.shared.config.AuthTransportProperties authTransportProperties;

  public MfaController(
      MfaProperties properties,
      MfaService mfaService,
      AuthService authService,
      AuthCookieService authCookieService,
      AuditService auditService,
      com.notebook.lumen.identity.shared.config.AuthTransportProperties authTransportProperties) {
    this.properties = properties;
    this.mfaService = mfaService;
    this.authService = authService;
    this.authCookieService = authCookieService;
    this.auditService = auditService;
    this.authTransportProperties = authTransportProperties;
  }

  @GetMapping("/settings")
  public MfaSettingsResponse settings(@AuthenticationPrincipal Jwt accessToken) {
    UUID userId = requireUserId(accessToken);
    if (!properties.enabled()) {
      return new MfaSettingsResponse(false, false, false, 0, 0, false, List.of());
    }
    int activeCredentialCount = mfaService.activeCredentials(userId).size();
    boolean webauthnEnabled = activeCredentialCount > 0;
    boolean backupEnabled = mfaService.hasRecoveryCodes(userId);
    return new MfaSettingsResponse(
        true,
        properties.webauthnEnabled(),
        backupEnabled,
        mfaService.recoveryCodesRemaining(userId),
        activeCredentialCount,
        webauthnEnabled,
        List.of("WEBAUTHN", "RECOVERY_CODE"));
  }

  @PostMapping("/webauthn/registration/options")
  public WebAuthnRegistrationOptionsResponse registrationOptions(@AuthenticationPrincipal Jwt accessToken) {
    UUID userId = requireUserId(accessToken);
    mfaService.requireWebauthnEnabled();
    String challenge = mfaService.createChallenge("webauthn:registration:", userId.toString());
    List<Map<String, String>> excludes =
        mfaService.activeCredentials(userId).stream()
            .map(c -> Map.of("id", c.getCredentialId(), "type", "public-key"))
            .toList();
    return new WebAuthnRegistrationOptionsResponse(
        challenge,
        properties.webauthn().rpId(),
        properties.webauthn().rpName(),
        Base64.getUrlEncoder().withoutPadding().encodeToString(userId.toString().getBytes(StandardCharsets.UTF_8)),
        accessToken.getClaimAsString("email"),
        accessToken.getClaimAsString("email"),
        excludes,
        properties.webauthn().requireUserVerification());
  }

  @PostMapping("/webauthn/registration/verify")
  public MfaCredentialResponse registrationVerify(
      @AuthenticationPrincipal Jwt accessToken,
      @Valid @RequestBody WebAuthnRegistrationVerifyRequest request) {
    UUID userId = requireUserId(accessToken);
    mfaService.requireWebauthnEnabled();
    validateOrigin(request.origin());
    mfaService.verifyAndConsumeChallenge("webauthn:registration:", userId.toString(), request.challenge());
    var credential =
        mfaService.storeCredential(
            userId,
            request.credentialId(),
            request.publicKeyCose(),
            request.signCount() == null ? 0L : request.signCount(),
            request.name());
    return new MfaCredentialResponse(
        credential.getCredentialId(),
        credential.getName(),
        String.valueOf(credential.getCreatedAt()),
        credential.getLastUsedAt() == null ? null : String.valueOf(credential.getLastUsedAt()),
        credential.getRevokedAt() == null ? null : String.valueOf(credential.getRevokedAt()));
  }

  @PostMapping("/webauthn/authentication/options")
  public WebAuthnAuthenticationOptionsResponse authenticationOptions(
      @Valid @RequestBody WebAuthnAuthenticationOptionsRequest request) {
    mfaService.requireWebauthnEnabled();
    UUID userId = mfaService.requireMfaSessionUser(request.mfaSessionId());
    String challenge = mfaService.createChallenge("webauthn:authentication:", request.mfaSessionId());
    return new WebAuthnAuthenticationOptionsResponse(
        request.mfaSessionId(),
        challenge,
        properties.webauthn().rpId(),
        mfaService.activeCredentials(userId).stream().map(c -> c.getCredentialId()).toList(),
        properties.webauthn().requireUserVerification());
  }

  @PostMapping("/webauthn/authentication/verify")
  public ResponseEntity<AuthResponse> authenticationVerify(
      @Valid @RequestBody WebAuthnAuthenticationVerifyRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    mfaService.requireWebauthnEnabled();
    UUID userId = mfaService.requireMfaSessionUser(request.mfaSessionId());
    validateOrigin(request.origin());
    mfaService.verifyAndConsumeChallenge(
        "webauthn:authentication:", request.mfaSessionId(), request.challenge());
    var credential =
        mfaService.activeCredentials(userId).stream()
            .filter(c -> c.getCredentialId().equals(request.credentialId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new MfaException(
                        "WEBAUTHN_CREDENTIAL_NOT_FOUND",
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Credential not found"));
    credential.markUsed(request.signCount() == null ? credential.getSignCount() : request.signCount());
    AuthResponse response = authService.completeMfaLogin(request.mfaSessionId(), httpRequest);
    return withCookieIfNeeded(response, httpRequest, httpResponse);
  }

  @PostMapping("/recovery-codes/generate")
  public RecoveryCodeGenerateResponse generateRecoveryCodes(
      @AuthenticationPrincipal Jwt accessToken,
      HttpServletRequest httpRequest,
      @RequestBody(required = false) RecoveryCodeGenerateRequest request) {
    UUID userId = requireUserId(accessToken);
    boolean hadActiveCodes = mfaService.hasRecoveryCodes(userId);
    if (mfaService.hasRecoveryCodes(userId)
        && (request == null || !request.acknowledgeReplace())) {
      throw new MfaException(
          "MFA_RECOVERY_REGEN_ACK_REQUIRED",
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Recovery code regeneration acknowledgement is required");
    }
    var response = new RecoveryCodeGenerateResponse(mfaService.regenerateRecoveryCodes(userId));
    auditService.record(
        hadActiveCodes ? "MFA_RECOVERY_CODES_REGENERATED" : "MFA_RECOVERY_CODES_GENERATED",
        userId,
        "USER",
        userId,
        httpRequest,
        Map.of("codeCount", response.codes().size()));
    return response;
  }

  @PostMapping("/recovery-codes/verify")
  public ResponseEntity<AuthResponse> verifyRecoveryCode(
      @Valid @RequestBody RecoveryCodeVerifyRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    UUID userId = mfaService.requireMfaSessionUser(request.mfaSessionId());
    mfaService.verifyRecoveryCode(userId, request.recoveryCode());
    auditService.record(
        "MFA_RECOVERY_CODE_USED",
        userId,
        "USER",
        userId,
        httpRequest,
        Map.of("method", "recovery_code"));
    AuthResponse response = authService.completeMfaLogin(request.mfaSessionId(), httpRequest);
    return withCookieIfNeeded(response, httpRequest, httpResponse);
  }

  @GetMapping("/webauthn/credentials")
  public List<MfaCredentialResponse> credentials(@AuthenticationPrincipal Jwt accessToken) {
    UUID userId = requireUserId(accessToken);
    mfaService.requireWebauthnEnabled();
    return mfaService.activeCredentials(userId).stream()
        .map(
            c ->
                new MfaCredentialResponse(
                    c.getCredentialId(),
                    c.getName(),
                    String.valueOf(c.getCreatedAt()),
                    c.getLastUsedAt() == null ? null : String.valueOf(c.getLastUsedAt()),
                    c.getRevokedAt() == null ? null : String.valueOf(c.getRevokedAt())))
        .toList();
  }

  @PatchMapping("/webauthn/credentials/{credentialId}")
  public ResponseEntity<Void> updateCredential(
      @AuthenticationPrincipal Jwt accessToken,
      @PathVariable String credentialId,
      @Valid @RequestBody CredentialRenameRequest request) {
    UUID userId = requireUserId(accessToken);
    mfaService.renameCredential(userId, credentialId, request.name());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/webauthn/credentials/{credentialId}")
  public ResponseEntity<Void> deleteCredential(
      @AuthenticationPrincipal Jwt accessToken,
      @PathVariable String credentialId,
      HttpServletRequest httpRequest) {
    UUID userId = requireUserId(accessToken);
    mfaService.removeCredential(userId, credentialId);
    auditService.record(
        "MFA_CREDENTIAL_REVOKED",
        userId,
        "USER",
        userId,
        httpRequest,
        Map.of("credentialId", credentialId));
    return ResponseEntity.noContent().build();
  }

  private UUID requireUserId(Jwt accessToken) {
    if (accessToken == null) {
      throw new MfaException(
          "MFA_REQUIRED", org.springframework.http.HttpStatus.UNAUTHORIZED, "MFA authentication required");
    }
    return UUID.fromString(accessToken.getSubject());
  }

  private void validateOrigin(String origin) {
    if (!mfaService.isAllowedOrigin(origin)) {
      throw new MfaException(
          "WEBAUTHN_ORIGIN_NOT_ALLOWED",
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "WebAuthn origin is not allowed");
    }
  }

  private ResponseEntity<AuthResponse> withCookieIfNeeded(
      AuthResponse response, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    if (authTransportProperties.cookieTransportEnabled() && !response.mfaRequired()) {
      authCookieService.writeAuthCookies(httpResponse, httpRequest, response);
    }
    if (authTransportProperties.bearerTransportEnabled()) {
      return ResponseEntity.ok(response);
    }
    return ResponseEntity.ok(
        new AuthResponse(
            null,
            null,
            "Cookie",
            response.expiresIn(),
            response.user(),
            response.mfaRequired(),
            response.mfaSessionId(),
            response.availableMethods()));
  }
}

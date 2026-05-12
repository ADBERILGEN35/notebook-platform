package com.notebook.lumen.identity.breakglass;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/break-glass")
public class BreakGlassController {

  private final BreakGlassService breakGlassService;

  public BreakGlassController(BreakGlassService breakGlassService) {
    this.breakGlassService = breakGlassService;
  }

  @PostMapping(
      path = "/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassDtos.BreakGlassLoginResponse login(
      @Valid @RequestBody BreakGlassDtos.BreakGlassLoginRequest request,
      HttpServletRequest httpRequest) {
    // Never log token or reason.
    String token = request == null ? "" : request.token();
    String reason = request == null ? "" : request.reason();
    return breakGlassService.loginStaticToken(token, reason, httpRequest);
  }

  @PostMapping(
      path = "/webauthn/challenge",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassDtos.BreakGlassWebauthnChallengeResponse webauthnChallenge(
      @Valid @RequestBody BreakGlassDtos.BreakGlassWebauthnChallengeRequest request) {
    return breakGlassService.createWebauthnChallenge(request == null ? "" : request.reason());
  }

  @PostMapping(
      path = "/webauthn/verify",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassDtos.BreakGlassLoginResponse webauthnVerify(
      @Valid @RequestBody BreakGlassDtos.BreakGlassWebauthnVerifyRequest request,
      HttpServletRequest httpRequest) {
    return breakGlassService.verifyWebauthn(
        request == null ? "" : request.challengeId(),
        request == null ? "" : request.credential(),
        request == null ? "" : request.reason(),
        httpRequest);
  }

  @PostMapping(
      path = "/offline-signed/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public BreakGlassDtos.BreakGlassLoginResponse offlineSignedLogin(
      @Valid @RequestBody BreakGlassDtos.BreakGlassOfflineSignedLoginRequest request,
      HttpServletRequest httpRequest) {
    return breakGlassService.loginOfflineSigned(
        request == null ? "" : request.assertion(), request == null ? "" : request.reason(), httpRequest);
  }

  /**
   * Read-only readiness status. Safe to expose publicly: never includes token/hash.
   *
   * <p>Kept as a separate endpoint so runbooks/CLI can quickly check posture without admin access.
   */
  @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> status() {
    BreakGlassDtos.BreakGlassStatusResponse s = breakGlassService.status();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("enabled", s.enabled());
    out.put("credentialMode", s.credentialMode());
    out.put("allowedModes", s.allowedModes());
    out.put("staticTokenConfigured", s.staticTokenConfigured());
    out.put("webauthnEnabled", s.webauthnEnabled());
    out.put("webauthnCredentialCount", s.webauthnCredentialCount());
    out.put("offlineSignedEnabled", s.offlineSignedEnabled());
    out.put("offlinePublicKeyConfigured", s.offlinePublicKeyConfigured());
    out.put("rotationRecommended", s.staticTokenRotationRecommended());
    out.put("lastStaticTokenUsedAt", s.lastStaticTokenUsedAt());
    out.put("approvalMode", s.approvalMode());
    out.put("pendingReviewCount", s.pendingReviewCount());
    out.put("overdueReviewCount", s.overdueReviewCount());
    out.put("sessionTtlMinutes", s.sessionTtlMinutes());
    out.put("maxActiveSessions", s.maxActiveSessions());
    out.put("requireReason", s.requireReason());
    out.put("requireMfa", s.requireMfa());
    out.put("rotationTrackingEnabled", s.rotationTrackingEnabled());
    out.put("rotationRequired", s.rotationRequired());
    out.put("openRotationEvents", s.openRotationEvents());
    out.put("oldestRotationRequiredAt", s.oldestRotationRequiredAt());
    out.put("lastRotationVerifiedAt", s.lastRotationVerifiedAt());
    return out;
  }
}


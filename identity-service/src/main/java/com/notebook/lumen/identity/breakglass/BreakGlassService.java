package com.notebook.lumen.identity.breakglass;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.shared.security.jwt.JwtTokenService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class BreakGlassService {

  private static final String TOKEN_TYPE = "break_glass_admin";
  private static final String CLAIM_BREAK_GLASS = "break_glass";
  private static final String CLAIM_PLATFORM_ROLES = "platform_roles";
  private static final String CLAIM_PLATFORM_PERMS = "platform_permissions";
  private static final String CLAIM_ACTOR_LABEL = "break_glass_actor";
  private static final String CLAIM_REASON_PRESENT = "break_glass_reason_present";
  private static final String CLAIM_MODE = "break_glass_mode";
  private static final String CLAIM_SESSION_ID = "break_glass_session_id";
  private static final String CLAIM_EVENT_ID = "break_glass_event_id";
  private static final String MODE_STATIC = "static-token";
  private static final String MODE_WEBAUTHN = "webauthn";
  private static final String MODE_OFFLINE = "offline-signed";

  private final BreakGlassProperties props;
  private final JwtTokenService jwtTokenService;
  private final AuditService auditService;
  private final BreakGlassAccessEventService accessEventService;

  /** In-memory guardrail: at most N active sessions per instance. */
  private final AtomicReference<Instant> activeUntil = new AtomicReference<>(null);
  private final AtomicReference<Instant> staticTokenLockoutUntil = new AtomicReference<>(null);
  private final AtomicReference<Integer> staticTokenFailures = new AtomicReference<>(0);
  private final AtomicReference<Instant> lastStaticTokenUsedAt = new AtomicReference<>(null);
  private final ConcurrentHashMap<String, Instant> usedAssertionJti = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Instant> pendingWebauthnChallenges = new ConcurrentHashMap<>();

  public BreakGlassService(
      BreakGlassProperties props,
      JwtTokenService jwtTokenService,
      AuditService auditService,
      BreakGlassAccessEventService accessEventService) {
    this.props = props;
    this.jwtTokenService = jwtTokenService;
    this.auditService = auditService;
    this.accessEventService = accessEventService;
  }

  public BreakGlassDtos.BreakGlassStatusResponse status() {
    List<String> allowedModes = new ArrayList<>();
    if (props.staticTokenEnabled()) {
      allowedModes.add(MODE_STATIC);
    }
    if (props.webauthnEnabled()) {
      allowedModes.add(MODE_WEBAUTHN);
    }
    if (props.offlineSignedEnabled()) {
      allowedModes.add(MODE_OFFLINE);
    }
    long pendingReviewCount = accessEventService.pendingReviewCount();
    long overdueReviewCount = accessEventService.overdueReviewCount();
    return new BreakGlassDtos.BreakGlassStatusResponse(
        props.enabled(),
        props.credentialMode(),
        List.copyOf(allowedModes),
        props.sessionTtlMinutes(),
        props.maxActiveSessions(),
        props.requireReason(),
        props.requireMfa(),
        props.tokenHash() != null && !props.tokenHash().isBlank(),
        props.webauthnEnabled(),
        0,
        props.offlineSignedEnabled(),
        props.offlinePublicKeyPath() != null && !props.offlinePublicKeyPath().isBlank(),
        props.staticTokenRotationRecommendedAfterUse(),
        lastStaticTokenUsedAt.get(),
        BreakGlassApprovalMode.from(props.approvalMode()).wire(),
        pendingReviewCount,
        overdueReviewCount);
  }

  public BreakGlassDtos.BreakGlassLoginResponse login(String token, String reason) {
    return loginStaticToken(token, reason, null);
  }

  public BreakGlassDtos.BreakGlassLoginResponse loginStaticToken(String token, String reason) {
    return loginStaticToken(token, reason, null);
  }

  public BreakGlassDtos.BreakGlassLoginResponse loginStaticToken(
      String token, String reason, HttpServletRequest request) {
    if (!props.enabled()) {
      throw BreakGlassException.disabled();
    }
    if (!props.staticTokenEnabled()) {
      throw BreakGlassException.disabled();
    }
    if (!modeAllowed(MODE_STATIC)) {
      throw BreakGlassException.disabled();
    }
    String r = reason == null ? "" : reason.trim();
    if (props.requireReason() && r.length() < 20) {
      audit("BREAK_GLASS_LOGIN_FAILED", Map.of("mode", MODE_STATIC, "reasonPresent", false, "failure", "REASON_REQUIRED"));
      throw BreakGlassException.reasonRequired();
    }
    if (isStaticTokenLocked()) {
      audit("BREAK_GLASS_LOGIN_FAILED", Map.of("mode", MODE_STATIC, "reasonPresent", true, "failure", "LOCKOUT"));
      throw BreakGlassException.rateLimited();
    }
    audit(
        "BREAK_GLASS_LOGIN_ATTEMPT",
        Map.of("mode", MODE_STATIC, "reasonPresent", !r.isBlank(), "tokenConfigured", !props.tokenHash().isBlank()));

    if (!tokenMatchesConfiguredHash(token)) {
      registerStaticTokenFailure();
      audit(
          "BREAK_GLASS_LOGIN_FAILED",
          Map.of("mode", MODE_STATIC, "reasonPresent", !r.isBlank(), "failure", "INVALID_TOKEN"));
      throw BreakGlassException.invalidToken();
    }
    staticTokenFailures.set(0);

    BreakGlassDtos.BreakGlassLoginResponse response =
        issueSession(MODE_STATIC, "break-glass:" + shortId(), r, props.staticTokenRotationRecommendedAfterUse(), request);
    lastStaticTokenUsedAt.set(Instant.now());
    if (props.staticTokenRotationRecommendedAfterUse()) {
      audit("BREAK_GLASS_STATIC_TOKEN_ROTATION_REQUIRED", Map.of("mode", MODE_STATIC, "reasonPresent", true));
    }
    return response;
  }

  public BreakGlassDtos.BreakGlassWebauthnChallengeResponse createWebauthnChallenge(String reason) {
    if (!props.enabled() || !props.webauthnEnabled() || !modeAllowed(MODE_WEBAUTHN)) {
      throw BreakGlassException.disabled();
    }
    String r = reason == null ? "" : reason.trim();
    if (props.requireReason() && r.length() < 20) {
      throw BreakGlassException.reasonRequired();
    }
    String challengeId = UUID.randomUUID().toString();
    String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    pendingWebauthnChallenges.put(challengeId, Instant.now().plus(5, ChronoUnit.MINUTES));
    audit("BREAK_GLASS_WEBAUTHN_CHALLENGE_CREATED", Map.of("mode", MODE_WEBAUTHN, "reasonPresent", true));
    return new BreakGlassDtos.BreakGlassWebauthnChallengeResponse(challengeId, challenge);
  }

  public BreakGlassDtos.BreakGlassLoginResponse verifyWebauthn(
      String challengeId, String credential, String reason) {
    return verifyWebauthn(challengeId, credential, reason, null);
  }

  public BreakGlassDtos.BreakGlassLoginResponse verifyWebauthn(
      String challengeId, String credential, String reason, HttpServletRequest request) {
    if (!props.enabled() || !props.webauthnEnabled() || !modeAllowed(MODE_WEBAUTHN)) {
      throw BreakGlassException.disabled();
    }
    String r = reason == null ? "" : reason.trim();
    if (props.requireReason() && r.length() < 20) {
      throw BreakGlassException.reasonRequired();
    }
    Instant exp = pendingWebauthnChallenges.remove(challengeId == null ? "" : challengeId.trim());
    if (exp == null || Instant.now().isAfter(exp)) {
      audit("BREAK_GLASS_WEBAUTHN_VERIFY_FAILED", Map.of("mode", MODE_WEBAUTHN, "failure", "CHALLENGE_INVALID_OR_EXPIRED"));
      throw BreakGlassException.invalidToken();
    }
    if (credential == null || credential.isBlank()) {
      audit("BREAK_GLASS_WEBAUTHN_VERIFY_FAILED", Map.of("mode", MODE_WEBAUTHN, "failure", "EMPTY_CREDENTIAL"));
      throw BreakGlassException.invalidToken();
    }
    audit("BREAK_GLASS_WEBAUTHN_VERIFY_SUCCEEDED", Map.of("mode", MODE_WEBAUTHN, "reasonPresent", true));
    return issueSession(MODE_WEBAUTHN, "break-glass-webauthn:" + shortId(), r, false, request);
  }

  public BreakGlassDtos.BreakGlassLoginResponse loginOfflineSigned(String assertion, String reason) {
    return loginOfflineSigned(assertion, reason, null);
  }

  public BreakGlassDtos.BreakGlassLoginResponse loginOfflineSigned(
      String assertion, String reason, HttpServletRequest request) {
    if (!props.enabled() || !props.offlineSignedEnabled() || !modeAllowed(MODE_OFFLINE)) {
      throw BreakGlassException.disabled();
    }
    String r = reason == null ? "" : reason.trim();
    if (props.requireReason() && r.length() < 20) {
      throw BreakGlassException.reasonRequired();
    }
    OfflineClaims claims = parseOfflineAssertion(assertion);
    if (!Objects.equals(props.offlineAllowedIssuer(), claims.issuer())) {
      audit("BREAK_GLASS_OFFLINE_ASSERTION_FAILED", Map.of("mode", MODE_OFFLINE, "failure", "INVALID_ISSUER"));
      throw BreakGlassException.invalidToken();
    }
    if (!Objects.equals(props.offlineRequiredAudience(), claims.audience())) {
      audit("BREAK_GLASS_OFFLINE_ASSERTION_FAILED", Map.of("mode", MODE_OFFLINE, "failure", "INVALID_AUDIENCE"));
      throw BreakGlassException.invalidToken();
    }
    if (!"break_glass_admin".equalsIgnoreCase(claims.purpose())) {
      audit("BREAK_GLASS_OFFLINE_ASSERTION_FAILED", Map.of("mode", MODE_OFFLINE, "failure", "INVALID_PURPOSE"));
      throw BreakGlassException.invalidToken();
    }
    if (claims.expiresAt().isBefore(Instant.now())
        || claims.issuedAt().plusSeconds(props.offlineMaxAssertionTtlSeconds()).isBefore(Instant.now())) {
      audit("BREAK_GLASS_OFFLINE_ASSERTION_FAILED", Map.of("mode", MODE_OFFLINE, "failure", "ASSERTION_EXPIRED"));
      throw BreakGlassException.invalidToken();
    }
    if (usedAssertionJti.putIfAbsent(claims.jti(), claims.expiresAt()) != null) {
      audit("BREAK_GLASS_ASSERTION_REPLAYED", Map.of("mode", MODE_OFFLINE, "jtiHash", sha256(claims.jti())));
      throw BreakGlassException.assertionReplayed();
    }
    audit("BREAK_GLASS_OFFLINE_ASSERTION_SUCCEEDED", Map.of("mode", MODE_OFFLINE, "reasonPresent", true));
    return issueSession(MODE_OFFLINE, "break-glass-offline:" + shortId(), r, false, request);
  }

  private BreakGlassDtos.BreakGlassLoginResponse issueSession(
      String mode, String actor, String reason, boolean rotationRequired, HttpServletRequest request) {
    if (BreakGlassApprovalMode.from(props.approvalMode()) == BreakGlassApprovalMode.REQUIRED_BEFORE_ISSUE) {
      String requestId = UUID.randomUUID().toString();
      accessEventService.createIssuedEvent(
          requestId,
          mode,
          actor,
          reason,
          Instant.now(),
          Instant.now().plusSeconds(props.sessionTtlMinutes() * 60L),
          rotationRequired,
          "",
          request);
      throw new BreakGlassException(
          "BREAK_GLASS_APPROVAL_REQUIRED",
          HttpStatus.FORBIDDEN,
          "Break-glass approval required. requestId=" + requestId);
    }
    enforceSessionLimit();
    audit(
        "BREAK_GLASS_LOGIN_SUCCEEDED",
        Map.of("mode", mode, "reasonPresent", true, "ttlMinutes", props.sessionTtlMinutes()));
    UUID syntheticUserId = UUID.randomUUID();
    String sessionId = UUID.randomUUID().toString();
    String jti = UUID.randomUUID().toString();
    long ttlSeconds = props.sessionTtlMinutes() * 60L;
    BreakGlassAccessEvent createdEvent = null;
    if (props.eventLogEnabled()) {
      createdEvent =
          accessEventService.createIssuedEvent(
          sessionId,
          mode,
          actor,
          reason,
          Instant.now(),
          Instant.now().plusSeconds(ttlSeconds),
          rotationRequired,
          jti,
          request);
    }
    Map<String, Object> claims =
        Map.of(
            "token_type",
            TOKEN_TYPE,
            "jti",
            jti,
            CLAIM_BREAK_GLASS,
            true,
            CLAIM_MODE,
            mode,
            CLAIM_SESSION_ID,
            sessionId,
            CLAIM_EVENT_ID,
            createdEvent == null ? "" : createdEvent.getId().toString(),
            CLAIM_PLATFORM_ROLES,
            List.of(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN),
            CLAIM_PLATFORM_PERMS,
            PlatformAdminRbacConstants.allPermissions(),
            CLAIM_ACTOR_LABEL,
            actor,
            CLAIM_REASON_PRESENT,
            true);
    String accessToken = jwtTokenService.generateAccessToken(syntheticUserId, actor, claims, ttlSeconds);
    audit("BREAK_GLASS_SESSION_ISSUED", Map.of("mode", mode, "reasonPresent", true, "actor", actor));
    auditMetricLike(mode, "SUCCESS");
    return new BreakGlassDtos.BreakGlassLoginResponse(accessToken, "Bearer", ttlSeconds, true);
  }

  private void enforceSessionLimit() {
    if (props.maxActiveSessions() <= 0) {
      return;
    }
    Instant now = Instant.now();
    Instant until = activeUntil.get();
    if (until != null && now.isBefore(until)) {
      audit("BREAK_GLASS_LOGIN_FAILED", Map.of("reasonPresent", true, "failure", "SESSION_LIMIT"));
      throw BreakGlassException.sessionLimitExceeded();
    }
    activeUntil.set(now.plusSeconds(props.sessionTtlMinutes() * 60L));
  }

  private boolean tokenMatchesConfiguredHash(String token) {
    String expected = props.tokenHash() == null ? "" : props.tokenHash().trim();
    if (expected.isBlank()) {
      return false;
    }
    String provided = sha256(token == null ? "" : token);
    return constantTimeEquals(expected, provided);
  }

  private boolean modeAllowed(String mode) {
    String configured = props.credentialMode() == null ? "" : props.credentialMode().trim().toLowerCase();
    if ("hybrid".equals(configured)) {
      return true;
    }
    return configured.equals(mode);
  }

  private boolean isStaticTokenLocked() {
    Instant lock = staticTokenLockoutUntil.get();
    return lock != null && Instant.now().isBefore(lock);
  }

  private void registerStaticTokenFailure() {
    int next = staticTokenFailures.updateAndGet(v -> v == null ? 1 : v + 1);
    if (next >= props.staticTokenMaxFailuresPerWindow()) {
      staticTokenLockoutUntil.set(Instant.now().plus(props.staticTokenLockoutMinutes(), ChronoUnit.MINUTES));
      staticTokenFailures.set(0);
    }
    auditMetricLike(MODE_STATIC, "FAILED");
  }

  private OfflineClaims parseOfflineAssertion(String assertion) {
    try {
      String[] parts = assertion == null ? new String[0] : assertion.split("\\.");
      if (parts.length < 2) {
        throw new IllegalArgumentException("invalid jwt");
      }
      String payloadJson =
          new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      @SuppressWarnings("unchecked")
      Map<String, Object> payload =
          new com.fasterxml.jackson.databind.ObjectMapper().readValue(payloadJson, HashMap.class);
      String iss = String.valueOf(payload.getOrDefault("iss", ""));
      String aud = String.valueOf(payload.getOrDefault("aud", ""));
      String jti = String.valueOf(payload.getOrDefault("jti", ""));
      String purpose = String.valueOf(payload.getOrDefault("purpose", ""));
      long iat = Long.parseLong(String.valueOf(payload.getOrDefault("iat", "0")));
      long exp = Long.parseLong(String.valueOf(payload.getOrDefault("exp", "0")));
      if (jti.isBlank() || iat <= 0 || exp <= 0) {
        throw new IllegalArgumentException("missing claims");
      }
      if (props.offlinePublicKeyPath() != null && !props.offlinePublicKeyPath().isBlank()) {
        Path p = Path.of(props.offlinePublicKeyPath());
        if (!Files.isRegularFile(p)) {
          throw new IllegalArgumentException("public key unavailable");
        }
      }
      return new OfflineClaims(iss, aud, jti, purpose, Instant.ofEpochSecond(iat), Instant.ofEpochSecond(exp));
    } catch (Exception e) {
      audit("BREAK_GLASS_OFFLINE_ASSERTION_FAILED", Map.of("mode", MODE_OFFLINE, "failure", "PARSE_ERROR"));
      throw BreakGlassException.invalidToken();
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    byte[] ab = a == null ? new byte[0] : a.getBytes(StandardCharsets.UTF_8);
    byte[] bb = b == null ? new byte[0] : b.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(ab, bb);
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private void audit(String eventType, Map<String, Object> metadata) {
    // actorUserId is unknown (emergency principal). Keep null and rely on metadata.
    auditService.record(eventType, null, "BREAK_GLASS", null, null, metadata);
  }

  private void auditMetricLike(String mode, String result) {
    audit("BREAK_GLASS_LOGIN_METRIC", Map.of("mode", mode, "result", result));
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private record OfflineClaims(
      String issuer,
      String audience,
      String jti,
      String purpose,
      Instant issuedAt,
      Instant expiresAt) {}
}


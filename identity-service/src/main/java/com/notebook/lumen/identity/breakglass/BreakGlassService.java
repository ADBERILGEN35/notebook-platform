package com.notebook.lumen.identity.breakglass;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.shared.security.jwt.JwtTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class BreakGlassService {

  private static final String TOKEN_TYPE = "break_glass_admin";
  private static final String CLAIM_BREAK_GLASS = "break_glass";
  private static final String CLAIM_PLATFORM_ROLES = "platform_roles";
  private static final String CLAIM_PLATFORM_PERMS = "platform_permissions";
  private static final String CLAIM_ACTOR_LABEL = "break_glass_actor";
  private static final String CLAIM_REASON_PRESENT = "break_glass_reason_present";

  private final BreakGlassProperties props;
  private final JwtTokenService jwtTokenService;
  private final AuditService auditService;

  /** In-memory guardrail: at most N active sessions per instance. */
  private final AtomicReference<Instant> activeUntil = new AtomicReference<>(null);

  public BreakGlassService(
      BreakGlassProperties props, JwtTokenService jwtTokenService, AuditService auditService) {
    this.props = props;
    this.jwtTokenService = jwtTokenService;
    this.auditService = auditService;
  }

  public BreakGlassDtos.BreakGlassStatusResponse status() {
    return new BreakGlassDtos.BreakGlassStatusResponse(
        props.enabled(),
        props.sessionTtlMinutes(),
        props.maxActiveSessions(),
        props.requireReason(),
        props.requireMfa(),
        props.tokenHash() != null && !props.tokenHash().isBlank());
  }

  public BreakGlassDtos.BreakGlassLoginResponse login(String token, String reason) {
    if (!props.enabled()) {
      throw BreakGlassException.disabled();
    }
    String r = reason == null ? "" : reason.trim();
    if (props.requireReason() && r.length() < 20) {
      audit("BREAK_GLASS_LOGIN_FAILED", Map.of("reasonPresent", false, "failure", "REASON_REQUIRED"));
      throw BreakGlassException.reasonRequired();
    }
    audit(
        "BREAK_GLASS_LOGIN_ATTEMPT",
        Map.of("reasonPresent", !r.isBlank(), "tokenConfigured", !props.tokenHash().isBlank()));

    if (!tokenMatchesConfiguredHash(token)) {
      audit(
          "BREAK_GLASS_LOGIN_FAILED",
          Map.of("reasonPresent", !r.isBlank(), "failure", "INVALID_TOKEN"));
      throw BreakGlassException.invalidToken();
    }

    enforceSessionLimit();

    // No refresh token for break-glass.
    String actor = "break-glass:" + shortId();
    Map<String, Object> claims =
        Map.of(
            "token_type",
            TOKEN_TYPE,
            CLAIM_BREAK_GLASS,
            true,
            CLAIM_PLATFORM_ROLES,
            List.of(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN),
            CLAIM_PLATFORM_PERMS,
            PlatformAdminRbacConstants.allPermissions(),
            CLAIM_ACTOR_LABEL,
            actor,
            CLAIM_REASON_PRESENT,
            true);

    // JwtTokenService requires UUID subject; we use a random UUID and put the actor label separately.
    UUID syntheticUserId = UUID.randomUUID();
    long ttlSeconds = props.sessionTtlMinutes() * 60L;
    String accessToken =
        jwtTokenService.generateAccessToken(syntheticUserId, actor, claims, ttlSeconds);
    long expiresIn = ttlSeconds;

    audit(
        "BREAK_GLASS_LOGIN_SUCCEEDED",
        Map.of("reasonPresent", true, "ttlMinutes", props.sessionTtlMinutes()));
    audit("BREAK_GLASS_SESSION_ISSUED", Map.of("reasonPresent", true, "actor", actor));
    return new BreakGlassDtos.BreakGlassLoginResponse(accessToken, "Bearer", expiresIn, true);
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

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}


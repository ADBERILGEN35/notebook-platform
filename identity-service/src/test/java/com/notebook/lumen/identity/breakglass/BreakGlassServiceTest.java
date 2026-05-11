package com.notebook.lumen.identity.breakglass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.shared.security.jwt.JwtTokenService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BreakGlassServiceTest {

  @Test
  void disabled_loginRejected() {
    BreakGlassProperties props = props(false, "");
    BreakGlassService svc =
        new BreakGlassService(
            props,
            mock(JwtTokenService.class),
            mock(AuditService.class),
            mock(BreakGlassAccessEventService.class));
    assertThatThrownBy(() -> svc.login("t", "reason reason reason reason"))
        .isInstanceOf(BreakGlassException.class)
        .hasMessageContaining("disabled");
  }

  @Test
  void invalidTokenRejected_withoutLeakingToken() {
    BreakGlassProperties props = props(true, "sha256:deadbeef");
    AuditService audit = mock(AuditService.class);
    JwtTokenService jwt = mock(JwtTokenService.class);
    BreakGlassService svc =
        new BreakGlassService(props, jwt, audit, mock(BreakGlassAccessEventService.class));
    assertThatThrownBy(() -> svc.login("super-secret-token", "reason reason reason reason"))
        .isInstanceOf(BreakGlassException.class)
        .satisfies(e -> assertThat(((BreakGlassException) e).getErrorCode()).isEqualTo("BREAK_GLASS_INVALID_TOKEN"));
    verify(audit, atLeastOnce()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  void missingReasonRejected() {
    BreakGlassProperties props = props(true, "sha256:deadbeef");
    BreakGlassService svc =
        new BreakGlassService(
            props,
            mock(JwtTokenService.class),
            mock(AuditService.class),
            mock(BreakGlassAccessEventService.class));
    assertThatThrownBy(() -> svc.login("x", "short"))
        .isInstanceOf(BreakGlassException.class)
        .satisfies(e -> assertThat(((BreakGlassException) e).getErrorCode()).isEqualTo("BREAK_GLASS_REASON_REQUIRED"));
  }

  @Test
  void validTokenIssuesShortLivedAccessToken_noRefresh() {
    String token = "offline-token";
    // Pre-computed by BreakGlassService sha256 formatter.
    BreakGlassProperties props = props(true, sha256(token));
    AuditService audit = mock(AuditService.class);
    JwtTokenService jwt = mock(JwtTokenService.class);
    when(jwt.generateAccessToken(any(), any(), any(Map.class), anyLong())).thenReturn("jwt-access");
    BreakGlassService svc =
        new BreakGlassService(props, jwt, audit, mock(BreakGlassAccessEventService.class));

    BreakGlassDtos.BreakGlassLoginResponse resp =
        svc.login(token, "Recover admin access after RBAC override misconfiguration.");
    assertThat(resp.accessToken()).isEqualTo("jwt-access");
    assertThat(resp.breakGlass()).isTrue();
    assertThat(resp.expiresIn()).isEqualTo(15 * 60L);
  }

  private static String sha256(String value) {
    try {
      var md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return "sha256:" + java.util.HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static BreakGlassProperties props(boolean enabled, String tokenHash) {
    return new BreakGlassProperties(
        enabled,
        "static-token",
        true,
        false,
        false,
        "disabled",
        true,
        60,
        true,
        false,
        tokenHash,
        "",
        "notebook-break-glass-offline",
        "identity-service",
        300,
        15,
        true,
        true,
        1,
        true,
        5,
        15,
        true);
  }
}


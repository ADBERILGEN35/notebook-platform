package com.notebook.lumen.identity.mfa.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.auth.application.AuthCookieService;
import com.notebook.lumen.identity.auth.application.AuthService;
import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.mfa.application.MfaService;
import com.notebook.lumen.identity.mfa.MfaProperties;
import com.notebook.lumen.identity.shared.config.AuthTransportProperties;
import com.notebook.lumen.identity.shared.exception.MfaException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class MfaControllerTest {
  @Test
  void settingsReturnsDisabledStateWhenMfaFeatureOff() {
    MfaService mfaService = mock(MfaService.class);
    MfaController controller =
        new MfaController(
            new MfaProperties(false, new MfaProperties.Webauthn(false, "localhost", "rp", "", "preferred"), 300, false),
            mfaService,
            mock(AuthService.class),
            mock(AuthCookieService.class),
            mock(AuditService.class),
            new AuthTransportProperties("bearer", false, "Lax", "", "/", "__Host-a", "__Host-r", "XSRF", "X-CSRF"));
    var response = controller.settings(jwt());
    assertThat(response.mfaEnabled()).isFalse();
    assertThat(response.webauthnEnabled()).isFalse();
  }

  @Test
  void webauthnOptionsFailsWhenWebauthnDisabled() {
    MfaService mfaService = mock(MfaService.class);
    when(mfaService.activeCredentials(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
    MfaController controller =
        new MfaController(
            new MfaProperties(true, new MfaProperties.Webauthn(false, "localhost", "rp", "", "preferred"), 300, false),
            mfaService,
            mock(AuthService.class),
            mock(AuthCookieService.class),
            mock(AuditService.class),
            new AuthTransportProperties("bearer", false, "Lax", "", "/", "__Host-a", "__Host-r", "XSRF", "X-CSRF"));
    doThrow(new MfaException("MFA_NOT_ENABLED", org.springframework.http.HttpStatus.NOT_IMPLEMENTED, "WebAuthn is not enabled"))
        .when(mfaService)
        .requireWebauthnEnabled();
    assertThatThrownBy(() -> controller.registrationOptions(jwt()))
        .isInstanceOf(MfaException.class)
        .hasMessageContaining("WebAuthn");
  }

  private Jwt jwt() {
    return new Jwt(
        "token",
        java.time.Instant.now(),
        java.time.Instant.now().plusSeconds(60),
        Map.of("alg", "none"),
        Map.of("sub", UUID.randomUUID().toString(), "token_type", "access"));
  }
}

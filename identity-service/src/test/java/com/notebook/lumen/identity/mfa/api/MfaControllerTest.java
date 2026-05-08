package com.notebook.lumen.identity.mfa.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.identity.mfa.MfaProperties;
import com.notebook.lumen.identity.shared.exception.MfaNotEnabledException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class MfaControllerTest {
  @Test
  void settingsReturnsDisabledStateWhenMfaFeatureOff() {
    MfaController controller = new MfaController(new MfaProperties(false, false, 300, false));
    var response = controller.settings(jwt());
    assertThat(response.mfaEnabled()).isFalse();
    assertThat(response.webauthnEnabled()).isFalse();
  }

  @Test
  void webauthnOptionsFailsWhenWebauthnDisabled() {
    MfaController controller = new MfaController(new MfaProperties(true, false, 300, false));
    assertThatThrownBy(() -> controller.registrationOptions(jwt(), null))
        .isInstanceOf(MfaNotEnabledException.class)
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

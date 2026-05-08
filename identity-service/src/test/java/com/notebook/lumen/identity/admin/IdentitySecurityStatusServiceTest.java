package com.notebook.lumen.identity.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.identity.mfa.MfaProperties;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.siem.SiemProperties;
import com.notebook.lumen.identity.sso.SsoProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentitySecurityStatusServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void reflectsSsoScimMfaSiemWithoutSecretsInJson() throws Exception {
    var sso =
        new SsoProperties(
            true,
            300,
            false,
            "",
            "",
            List.of(
                new SsoProperties.Provider(
                    "oidc",
                    "https://issuer.example",
                    "cid",
                    "super-secret-client",
                    "openid",
                    "email",
                    "groups",
                    "admins",
                    "example.com")));
    var scim = new ScimProperties(true, "scim-token-value", "", true, "notebook-admins");
    var mfa =
        new MfaProperties(
            true, new MfaProperties.Webauthn(true, "localhost", "rp", "", "preferred"), 300, false);
    var siem =
        new SiemProperties(
            true,
            "generic-http",
            "https://siem.example/ingest",
            "bearer",
            "siem-secret-token",
            "",
            "",
            10,
            100,
            10,
            30,
            3600,
            true,
            30,
            30,
            90,
            false);

    var service = new IdentitySecurityStatusService(sso, scim, mfa, siem);
    IdentitySecurityStatusResponse body = service.build();

    assertThat(body.sso().enabled()).isTrue();
    assertThat(body.sso().providersConfigured()).isEqualTo(1);
    assertThat(body.sso().adminGroupMappingConfigured()).isTrue();
    assertThat(body.scim().tokenConfigured()).isTrue();
    assertThat(body.siem().secretConfigured()).isTrue();
    assertThat(body.siem().endpointConfigured()).isTrue();

    String json = mapper.writeValueAsString(body);
    assertThat(json).doesNotContain("scim-token-value", "siem-secret-token", "super-secret-client", "cid");
  }

  @Test
  void siemDisabled_yieldsNoSecretConfigured() {
    var sso = new SsoProperties(false, 300, false, "", "", List.of());
    var scim = new ScimProperties(false, "", "", false, "");
    var mfa =
        new MfaProperties(
            false, new MfaProperties.Webauthn(false, "", "", "", "preferred"), 300, false);
    var siem =
        new SiemProperties(
            false,
            "noop",
            "",
            "none",
            "",
            "",
            "",
            10,
            100,
            10,
            30,
            3600,
            false,
            30,
            30,
            90,
            false);

    var service = new IdentitySecurityStatusService(sso, scim, mfa, siem);
    assertThat(service.build().siem().secretConfigured()).isFalse();
  }
}

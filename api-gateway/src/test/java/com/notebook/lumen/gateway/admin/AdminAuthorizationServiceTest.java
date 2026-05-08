package com.notebook.lumen.gateway.admin;

import com.notebook.lumen.gateway.config.GatewayAdminProperties;
import com.notebook.lumen.gateway.config.GatewayAdminProperties.Audit;
import com.notebook.lumen.gateway.config.GatewayAdminProperties.Enterprise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthorizationServiceTest {

  @Test
  void allowsPlatformAdminRole() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true, false, "off", "webauthn,recovery_code", "", "", new Audit(true), new Enterprise(false)));
    Jwt jwt = jwt("user-1", "member@example.com", List.of("PLATFORM_ADMIN"));
    assertThat(service.isAdmin(jwt)).isTrue();
  }

  @Test
  void allowsPlatformAdminFromPlatformRolesClaim() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true, false, "off", "webauthn,recovery_code", "", "", new Audit(true), new Enterprise(false)));
    Instant now = Instant.now();
    Jwt jwt =
        new Jwt(
            "token",
            now.minusSeconds(10),
            now.plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", "user-1",
                "email", "member@example.com",
                "platform_roles", List.of("PLATFORM_ADMIN"),
                "token_type", "access"));
    assertThat(service.isAdmin(jwt)).isTrue();
  }

  @Test
  void allowsConfiguredEmail() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "",
                "admin@example.com",
                new Audit(true),
                new Enterprise(false)));
    Jwt jwt = jwt("user-2", "admin@example.com", List.of("ROLE_USER"));
    assertThat(service.isAdmin(jwt)).isTrue();
  }

  @Test
  void rejectsNonAdminUser() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "allowed-user",
                "admin@example.com",
                new Audit(true),
                new Enterprise(false)));
    Jwt jwt = jwt("user-3", "member@example.com", List.of("ROLE_USER"));
    assertThat(service.isAdmin(jwt)).isFalse();
  }

  @Test
  void requiresMfaInEnforceMode() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "enforce",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(false)));
    Jwt jwt = jwt("user-1", "member@example.com", List.of("PLATFORM_ADMIN"));
    assertThat(service.isAdmin(jwt)).isFalse();
  }

  @Test
  void allowsAdminWithAcceptedMfaMethod() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "enforce",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(false)));
    Instant now = Instant.now();
    Jwt jwt =
        new Jwt(
            "token",
            now.minusSeconds(10),
            now.plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", "user-1",
                "email", "member@example.com",
                "roles", List.of("PLATFORM_ADMIN"),
                "token_type", "access",
                "mfa_verified", true,
                "amr", List.of("pwd", "webauthn")));
    assertThat(service.isAdmin(jwt)).isTrue();
  }

  private static Jwt jwt(String sub, String email, List<String> roles) {
    Instant now = Instant.now();
    return new Jwt(
        "token",
        now.minusSeconds(10),
        now.plusSeconds(300),
        Map.of("alg", "RS256"),
        Map.of("sub", sub, "email", email, "roles", roles, "token_type", "access"));
  }
}

package com.notebook.lumen.gateway.admin;

import com.notebook.lumen.gateway.config.GatewayAdminProperties;
import com.notebook.lumen.gateway.config.GatewayAdminProperties.Audit;
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
        new AdminAuthorizationService(new GatewayAdminProperties(true, "", "", new Audit(true)));
    Jwt jwt = jwt("user-1", "member@example.com", List.of("PLATFORM_ADMIN"));
    assertThat(service.isAdmin(jwt)).isTrue();
  }

  @Test
  void allowsConfiguredEmail() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(true, "", "admin@example.com", new Audit(true)));
    Jwt jwt = jwt("user-2", "admin@example.com", List.of("ROLE_USER"));
    assertThat(service.isAdmin(jwt)).isTrue();
  }

  @Test
  void rejectsNonAdminUser() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(true, "allowed-user", "admin@example.com", new Audit(true)));
    Jwt jwt = jwt("user-3", "member@example.com", List.of("ROLE_USER"));
    assertThat(service.isAdmin(jwt)).isFalse();
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

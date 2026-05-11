package com.notebook.lumen.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.gateway.config.GatewayAdminProperties;
import com.notebook.lumen.gateway.config.GatewayAdminProperties.Audit;
import com.notebook.lumen.gateway.config.GatewayAdminProperties.Enterprise;
import com.notebook.lumen.gateway.config.GatewayAdminRbacProperties;
import com.notebook.lumen.gateway.config.GatewayAdminWriteProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AdminAuthorizationServiceTest {

  private static GatewayAdminWriteProperties writeOff() {
    return new GatewayAdminWriteProperties(false, "");
  }

  private static GatewayAdminRbacProperties rbacOff() {
    return new GatewayAdminRbacProperties(false);
  }

  private static GatewayAdminRbacProperties rbacOn() {
    return new GatewayAdminRbacProperties(true);
  }

  @Test
  void allowsPlatformAdminRole() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(false)),
            writeOff(),
            rbacOff());
    Jwt jwt = jwt("user-1", "member@example.com", List.of("PLATFORM_ADMIN"));
    assertThat(service.isAdmin(jwt)).isTrue();
  }

  @Test
  void allowsPlatformAdminFromPlatformRolesClaim() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(false)),
            writeOff(),
            rbacOff());
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
                new Enterprise(false)),
            writeOff(),
            rbacOff());
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
                new Enterprise(false)),
            writeOff(),
            rbacOff());
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
                new Enterprise(false)),
            writeOff(),
            rbacOff());
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
                new Enterprise(false)),
            writeOff(),
            rbacOff());
    Instant now = Instant.now();
    Jwt jwt =
        new Jwt(
            "token",
            now.minusSeconds(10),
            now.plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of(
                "sub",
                "user-1",
                "email",
                "member@example.com",
                "roles",
                List.of("PLATFORM_ADMIN"),
                "token_type",
                "access",
                "mfa_verified",
                true,
                "amr",
                List.of("pwd", "webauthn")));
    assertThat(service.isAdmin(jwt)).isTrue();
  }

  @Test
  void enterpriseWriteDeniesEmailAllowlistWithoutPlatformRole() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "",
                "ops@example.com",
                new Audit(true),
                new Enterprise(true)),
            new GatewayAdminWriteProperties(true, ""),
            rbacOff());
    Jwt jwt = jwt("user-2", "ops@example.com", List.of("ROLE_USER"));
    assertThat(service.adminWriteFeatureEnabled()).isTrue();
    assertThat(service.enterpriseAdminWriteDenialReason(jwt))
        .contains(ErrorCode.ADMIN_ACCESS_DENIED);
  }

  @Test
  void enterpriseWriteRequiresMfaWhenMfaModeWarn() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "warn",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(true)),
            new GatewayAdminWriteProperties(true, ""),
            rbacOff());
    Jwt jwt = jwt("user-1", "member@example.com", List.of("PLATFORM_ADMIN"));
    assertThat(service.enterpriseAdminWriteDenialReason(jwt))
        .contains(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
  }

  @Test
  void enterpriseWriteAllowsPlatformAdminWhenMfaOff() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(true)),
            new GatewayAdminWriteProperties(true, ""),
            rbacOff());
    Jwt jwt = jwt("user-1", "member@example.com", List.of("PLATFORM_ADMIN"));
    assertThat(service.enterpriseAdminWriteDenialReason(jwt)).isEmpty();
  }

  @Test
  void rbacEnforceDeniesAllowlistWithoutPermissionClaim() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "",
                "ops@example.com",
                new Audit(true),
                new Enterprise(false)),
            writeOff(),
            rbacOn());
    Jwt jwt = jwt("user-2", "ops@example.com", List.of("ROLE_USER"));
    assertThat(service.ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_AUDIT_READ))
        .contains(ErrorCode.ADMIN_PERMISSION_REQUIRED);
  }

  @Test
  void rbacEnforceAllowsExplicitPermissionClaim() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(false)),
            writeOff(),
            rbacOn());
    Instant now = Instant.now();
    Jwt jwt =
        new Jwt(
            "token",
            now.minusSeconds(10),
            now.plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of(
                "sub",
                "user-9",
                "email",
                "viewer@example.com",
                "roles",
                List.of("ROLE_USER"),
                "platform_permissions",
                List.of(PlatformAdminRbacConstants.PERM_AUDIT_READ),
                "token_type",
                "access"));
    assertThat(service.ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_AUDIT_READ))
        .isEmpty();
  }

  @Test
  void rbacEnforceOperationPermissionRequired() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(true)),
            new GatewayAdminWriteProperties(true, ""),
            rbacOn());
    Instant now = Instant.now();
    Jwt jwt =
        new Jwt(
            "token",
            now.minusSeconds(10),
            now.plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of(
                "sub",
                "user-a",
                "email",
                "author@example.com",
                "roles",
                List.of("ROLE_USER"),
                "platform_permissions",
                List.of(
                    PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CREATE,
                    PlatformAdminRbacConstants.PERM_SCIM_CHANGE_REQUEST_CREATE),
                "token_type",
                "access"));
    assertThat(service.ensureChangeRequestCreate(jwt, "ADMIN_MFA_MODE_UPDATE"))
        .contains(ErrorCode.ADMIN_OPERATION_PERMISSION_REQUIRED);
  }

  @Test
  void ensureAdminRbacRead_requiresPermissionWhenEnforced() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(true)),
            writeOff(),
            rbacOn());
    Instant now = Instant.now();
    Jwt jwt =
        new Jwt(
            "token",
            now.minusSeconds(10),
            now.plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of(
                "sub",
                "user-x",
                "email",
                "x@example.com",
                "platform_permissions",
                List.of(PlatformAdminRbacConstants.PERM_ENTERPRISE_STATUS_READ),
                "token_type",
                "access"));
    assertThat(service.ensureAdminRbacRead(jwt)).contains(ErrorCode.ADMIN_PERMISSION_REQUIRED);
  }

  @Test
  void ensureAdminRbacRead_allowsExplicitPermission() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(true)),
            writeOff(),
            rbacOn());
    Instant now = Instant.now();
    Jwt jwt =
        new Jwt(
            "token",
            now.minusSeconds(10),
            now.plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of(
                "sub",
                "user-x",
                "email",
                "x@example.com",
                "platform_permissions",
                List.of(PlatformAdminRbacConstants.PERM_RBAC_READ),
                "token_type",
                "access"));
    assertThat(service.ensureAdminRbacRead(jwt)).isEmpty();
  }

  @Test
  void ensureAdminRbacOverridesReload_requiresDedicatedPermission() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "off",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(true)),
            writeOff(),
            rbacOn());
    Instant now = Instant.now();
    Jwt jwt =
        new Jwt(
            "token",
            now.minusSeconds(10),
            now.plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of(
                "sub",
                "user-x",
                "email",
                "x@example.com",
                "platform_permissions",
                List.of(PlatformAdminRbacConstants.PERM_RBAC_READ),
                "token_type",
                "access"));
    assertThat(service.ensureAdminRbacOverridesReload(jwt))
        .contains(ErrorCode.ADMIN_PERMISSION_REQUIRED);
  }

  @Test
  void ensureAdminRbacOverridesReload_requiresMfaWhenMfaWarn() {
    AdminAuthorizationService service =
        new AdminAuthorizationService(
            new GatewayAdminProperties(
                true,
                false,
                "warn",
                "webauthn,recovery_code",
                "",
                "",
                new Audit(true),
                new Enterprise(true)),
            writeOff(),
            rbacOn());
    Instant now = Instant.now();
    Jwt noMfa =
        new Jwt(
            "token",
            now.minusSeconds(10),
            now.plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of(
                "sub",
                "user-x",
                "email",
                "x@example.com",
                "platform_permissions",
                List.of(PlatformAdminRbacConstants.PERM_RBAC_OVERRIDE_RELOAD),
                "token_type",
                "access"));
    assertThat(service.ensureAdminRbacOverridesReload(noMfa))
        .contains(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);

    Jwt withMfa =
        new Jwt(
            "token",
            now.minusSeconds(10),
            now.plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of(
                "sub",
                "user-x",
                "email",
                "x@example.com",
                "platform_permissions",
                List.of(PlatformAdminRbacConstants.PERM_RBAC_OVERRIDE_RELOAD),
                "token_type",
                "access",
                "mfa_verified",
                true,
                "amr",
                List.of("pwd", "webauthn")));
    assertThat(service.ensureAdminRbacOverridesReload(withMfa)).isEmpty();
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

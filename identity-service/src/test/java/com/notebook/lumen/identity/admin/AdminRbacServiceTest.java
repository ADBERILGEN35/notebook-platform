package com.notebook.lumen.identity.admin;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.sso.SsoProperties;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRbacServiceTest {

  @Test
  void mapIdpGroupsToRolesEmptyWhenRbacDisabled() {
    var props =
        new AdminRbacProperties(
            false,
            true,
            "admins",
            "audit-view",
            "",
            "",
            "",
            "",
            "",
            "",
            false,
            false,
            "");
    var svc = new AdminRbacService(props);
    assertThat(svc.mapIdpGroupsToRoles(null, List.of("audit-view"))).isEmpty();
  }

  @Test
  void mapIdpGroupsToRolesMatchesConfiguredGroup() {
    var props =
        new AdminRbacProperties(
            true,
            true,
            "",
            "notebook-audit-viewers",
            "",
            "",
            "",
            "",
            "",
            "",
            false,
            false,
            "");
    var svc = new AdminRbacService(props);
    assertThat(svc.mapIdpGroupsToRoles(null, List.of("Notebook-Audit-Viewers")))
        .containsExactly(PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER);
  }

  @Test
  void mapScimGroupKeysToRoles() {
    var props =
        new AdminRbacProperties(
            true,
            true,
            "",
            "",
            "",
            "",
            "",
            "",
            "notebook-change-approvers",
            "",
            false,
            false,
            "");
    var svc = new AdminRbacService(props);
    assertThat(svc.mapScimGroupKeysToRoles(Set.of("notebook-change-approvers")))
        .containsExactly(PlatformAdminRbacConstants.ROLE_PLATFORM_CHANGE_REQUEST_APPROVER);
  }

  @Test
  void resolvePermissionsPlatformAdminCoversAll() {
    var svc =
        new AdminRbacService(
            new AdminRbacProperties(true, true, "", "", "", "", "", "", "", "", false, false, ""));
    assertThat(svc.resolvePermissions(List.of(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN)))
        .containsExactlyInAnyOrderElementsOf(PlatformAdminRbacConstants.allPermissions());
  }

  @Test
  void resolvePermissionsAuditViewer() {
    var svc =
        new AdminRbacService(
            new AdminRbacProperties(true, true, "", "", "", "", "", "", "", "", false, false, ""));
    assertThat(svc.resolvePermissions(List.of(PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER)))
        .containsExactly(PlatformAdminRbacConstants.PERM_AUDIT_READ);
  }

  @Test
  void resolvePermissionsEmptyWhenRbacDisabled() {
    var off =
        new AdminRbacService(
            new AdminRbacProperties(false, true, "", "", "", "", "", "", "", "", false, false, ""));
    assertThat(off.resolvePermissions(List.of(PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER))).isEmpty();
  }

  @Test
  void statusSnapshotReflectsConfiguredGroups() {
    var props =
        new AdminRbacProperties(
            true,
            false,
            "g-admin",
            "g-audit",
            "",
            "g-sec",
            "",
            "",
            "",
            "",
            false,
            false,
            "");
    var s = new AdminRbacService(props).statusSnapshot();
    assertThat(s.enabled()).isTrue();
    assertThat(s.legacyPlatformAdminImpliesAll()).isFalse();
    assertThat(s.platformAdmin()).isTrue();
    assertThat(s.auditViewer()).isTrue();
    assertThat(s.securityAdmin()).isTrue();
    assertThat(s.changeRequestApprover()).isFalse();
  }

  @Test
  void legacySsoAdminGroupStillGrantsPlatformAdminWhenRbacEnabled() {
    var props =
        new AdminRbacProperties(
            true,
            true,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            false,
            false,
            "");
    var provider =
        new SsoProperties.Provider(
            "oidc",
            "https://issuer",
            "client",
            "secret",
            "openid profile email",
            "email",
            "groups",
            "notebook-admins",
            "example.com");
    var svc = new AdminRbacService(props);
    assertThat(svc.mapIdpGroupsToRoles(provider, List.of("notebook-admins")))
        .contains(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN);
  }
}

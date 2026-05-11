package com.notebook.lumen.identity.admin.rbac.overrides;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminRbacOverrideEffectiveApplierTest {

  private final AdminRbacOverrideEffectiveApplier applier = new AdminRbacOverrideEffectiveApplier();

  @Test
  void grantAddsRoleNotInBase() {
    UUID u = UUID.randomUUID();
    LinkedHashSet<String> merged = new LinkedHashSet<>();
    LinkedHashSet<String> base = new LinkedHashSet<>();
    var row =
        new AdminRbacOverrideAssignmentRow(
            null,
            u,
            PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER,
            "GRANT",
            "change-request:cr-1",
            null,
            null,
            "APPROVED_FOR_APPLY",
            null,
            Instant.now(),
            "gitops");
    var out = applier.apply(u, merged, base, List.of(row));
    assertThat(out.effectiveRoles()).containsExactly(PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER);
  }

  @Test
  void revokeRemovesOnlyOverrideGrant() {
    UUID u = UUID.randomUUID();
    LinkedHashSet<String> merged = new LinkedHashSet<>();
    LinkedHashSet<String> base = new LinkedHashSet<>();
    var grant =
        new AdminRbacOverrideAssignmentRow(
            null,
            u,
            PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_EXPORTER,
            "GRANT",
            "change-request:a",
            null,
            null,
            "APPROVED_FOR_APPLY",
            null,
            Instant.now(),
            "gitops");
    var revoke =
        new AdminRbacOverrideAssignmentRow(
            null,
            u,
            PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_EXPORTER,
            "REVOKE",
            "change-request:b",
            null,
            null,
            "APPROVED_FOR_APPLY",
            null,
            Instant.now(),
            "gitops");
    applier.apply(u, merged, base, List.of(grant, revoke));
    assertThat(merged).isEmpty();
  }

  @Test
  void revokeDoesNotStripIdpRoleAndWarns() {
    UUID u = UUID.randomUUID();
    LinkedHashSet<String> merged = new LinkedHashSet<>(List.of(PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER));
    LinkedHashSet<String> base = new LinkedHashSet<>(merged);
    var revoke =
        new AdminRbacOverrideAssignmentRow(
            null,
            u,
            PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER,
            "REVOKE",
            "change-request:x",
            null,
            null,
            "APPROVED_FOR_APPLY",
            null,
            Instant.now(),
            "gitops");
    var out = applier.apply(u, merged, base, List.of(revoke));
    assertThat(merged).containsExactly(PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER);
    assertThat(out.userWarnings()).contains("OVERRIDE_REVOKE_IGNORED_NON_OVERRIDE_ROLE");
  }
}

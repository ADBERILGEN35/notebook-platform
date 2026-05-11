package com.notebook.lumen.identity.admin.gitops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class GitOpsYamlPatchServiceTest {

  private final GitOpsYamlPatchService svc = new GitOpsYamlPatchService();

  @Test
  void rbacPatchPlan_appendsAssignmentRow() {
    UUID cr = UUID.randomUUID();
    UUID req = UUID.randomUUID();
    UUID appr = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    GitOpsRbacPatchContext ctx = new GitOpsRbacPatchContext(cr, req, appr);
    GitOpsYamlPatchService.PatchPlan plan =
        svc.buildPatchPlan(
            "ADMIN_RBAC_ROLE_GRANT_REQUEST",
            "grant:platform_audit_viewer:" + target,
            "dev",
            ctx);
    assertThat(plan.relativePath()).isEqualTo("deploy/gitops/environments/dev/admin-rbac-overrides.yaml");
    assertThat(plan.newValue()).contains("PLATFORM_AUDIT_VIEWER");
    assertThat(plan.newValue()).contains("change-request:" + cr);
    assertThat(plan.newValue()).contains(req.toString());
    assertThat(plan.newValue()).contains(appr.toString());
    assertThat(plan.yamlAfter()).contains(target.toString());
  }

  @Test
  void rbacApplyToContent_idempotentAppend() {
    UUID cr = UUID.randomUUID();
    GitOpsRbacPatchContext ctx = new GitOpsRbacPatchContext(cr, UUID.randomUUID(), UUID.randomUUID());
    UUID target = UUID.randomUUID();
    String yaml =
        "adminRbacOverrides:\n  version: 1\n  assignments: []\n";
    String patched =
        svc.applyPatchToContent(
            yaml, "ADMIN_RBAC_ROLE_REVOKE_REQUEST", "revoke:platform_audit_viewer:" + target, ctx);
    assertThat(patched).contains("REVOKE");
    assertThat(patched).contains(target.toString());
  }

  @Test
  void rbac_unknownRole_rejected() {
    UUID target = UUID.randomUUID();
    GitOpsRbacPatchContext ctx = new GitOpsRbacPatchContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    assertThatThrownBy(
            () ->
                svc.buildPatchPlan(
                    "ADMIN_RBAC_ROLE_GRANT_REQUEST",
                    "grant:not_a_real_role:" + target,
                    "dev",
                    ctx))
        .isInstanceOf(AdminGitOpsException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_GITOPS_PATCH_FAILED");
  }

  @Test
  void rbac_requiresContext() {
    assertThatThrownBy(
            () ->
                svc.buildPatchPlan(
                    "ADMIN_RBAC_ROLE_GRANT_REQUEST",
                    "grant:platform_audit_viewer:" + UUID.randomUUID(),
                    "dev",
                    null))
        .isInstanceOf(AdminGitOpsException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_GITOPS_PATCH_FAILED");
  }

  @Test
  void valuesPatchPlan_stillWorks() {
    GitOpsYamlPatchService.PatchPlan plan =
        svc.buildPatchPlan("ADMIN_MFA_MODE_UPDATE", "enforce", "dev", null);
    assertThat(plan.relativePath()).endsWith("/values.yaml");
    assertThat(plan.yamlDotPath()).isEqualTo("config.gatewayAdminMfaMode");
  }
}

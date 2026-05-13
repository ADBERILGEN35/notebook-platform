package com.notebook.lumen.gateway.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdminPlatformRetentionProxyServiceTest {

  @Test
  void extractsActiveHoldScopes() {
    Map<String, Object> holds =
        Map.of(
            "items",
            List.of(
                Map.of("scope", "CONTENT", "status", "ACTIVE"),
                Map.of("scope", "WORKSPACE", "status", "ACTIVE"),
                Map.of("scope", "IDENTITY", "status", "RELEASED")));

    Set<String> scopes = AdminPlatformRetentionProxyService.extractHoldScopes(holds);

    assertThat(scopes).containsExactlyInAnyOrder("CONTENT", "WORKSPACE");
  }

  @Test
  void mergeContentPlanOverridesContentTargetFields() {
    Map<String, Object> plan = new LinkedHashMap<>();
    Map<String, Object> noteVersions = new LinkedHashMap<>();
    noteVersions.put("targetKey", "content.note_versions");
    noteVersions.put("eligibleCount", null);
    noteVersions.put("purgeableCount", 0);
    noteVersions.put("blockedByLegalHold", false);
    noteVersions.put("warnings", new java.util.ArrayList<>(List.of("Inventory only.")));
    Map<String, Object> identityUsers = new LinkedHashMap<>();
    identityUsers.put("targetKey", "identity.users");
    identityUsers.put("eligibleCount", null);
    plan.put("targets", new java.util.ArrayList<>(List.of(noteVersions, identityUsers)));
    Map<String, Object> content =
        Map.of(
            "targets",
            List.of(
                Map.of(
                    "targetKey",
                    "content.note_versions",
                    "status",
                    "DRY_RUN_READY",
                    "eligibleCount",
                    1200,
                    "purgeableCount",
                    1200,
                    "blockedByLegalHold",
                    false,
                    "warnings",
                    List.of("CONTENT_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING"))));

    AdminPlatformRetentionProxyService.mergeContentPlan(plan, content);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> targets = (List<Map<String, Object>>) plan.get("targets");
    Map<String, Object> versions = targets.get(0);
    assertThat(versions.get("eligibleCount")).isEqualTo(1200);
    assertThat(versions.get("purgeableCount")).isEqualTo(1200);
    assertThat(versions.get("status")).isEqualTo("DRY_RUN_READY");
    @SuppressWarnings("unchecked")
    List<Object> warnings = (List<Object>) versions.get("warnings");
    assertThat(warnings)
        .contains("Inventory only.", "CONTENT_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING");
    assertThat(targets.get(1).get("eligibleCount")).isNull();
  }

  @Test
  void addWarningDeduplicates() {
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("warnings", new java.util.ArrayList<>(List.of("X")));

    AdminPlatformRetentionProxyService.addWarning(plan, "CONTENT_RETENTION_SERVICE_UNAVAILABLE");
    AdminPlatformRetentionProxyService.addWarning(plan, "CONTENT_RETENTION_SERVICE_UNAVAILABLE");

    @SuppressWarnings("unchecked")
    List<Object> warnings = (List<Object>) plan.get("warnings");
    assertThat(warnings).containsExactly("X", "CONTENT_RETENTION_SERVICE_UNAVAILABLE");
  }
}

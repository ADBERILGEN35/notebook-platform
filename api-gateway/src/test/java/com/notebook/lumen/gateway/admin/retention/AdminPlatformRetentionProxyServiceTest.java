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

  @Test
  void mergeContentPlanHandlesSearchDocumentsTarget() {
    Map<String, Object> plan = new LinkedHashMap<>();
    Map<String, Object> searchDocs = new LinkedHashMap<>();
    searchDocs.put("targetKey", "content.search_documents");
    searchDocs.put("status", "DRY_RUN_READY");
    searchDocs.put("eligibleCount", null);
    searchDocs.put("purgeableCount", 0);
    searchDocs.put("blockedByLegalHold", false);
    searchDocs.put("warnings", new java.util.ArrayList<>());
    plan.put("targets", new java.util.ArrayList<>(List.of(searchDocs)));

    Map<String, Object> content =
        Map.of(
            "targets",
            List.of(
                Map.of(
                    "targetKey",
                    "content.search_documents",
                    "status",
                    "DRY_RUN_READY",
                    "eligibleCount",
                    420,
                    "purgeableCount",
                    420,
                    "blockedByLegalHold",
                    false,
                    "warnings",
                    List.of())));

    AdminPlatformRetentionProxyService.mergeContentPlan(plan, content);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> targets = (List<Map<String, Object>>) plan.get("targets");
    assertThat(targets).hasSize(1);
    Map<String, Object> merged = targets.get(0);
    assertThat(merged.get("targetKey")).isEqualTo("content.search_documents");
    assertThat(merged.get("status")).isEqualTo("DRY_RUN_READY");
    assertThat(merged.get("eligibleCount")).isEqualTo(420);
    assertThat(merged.get("purgeableCount")).isEqualTo(420);
  }

  @Test
  void mergeSearchPlanOverridesSearchTargetFields() {
    Map<String, Object> plan = new LinkedHashMap<>();
    Map<String, Object> stale = new LinkedHashMap<>();
    stale.put("targetKey", "search.documents_stale");
    stale.put("status", "INVENTORY_ONLY");
    stale.put("eligibleCount", null);
    stale.put("purgeableCount", 0);
    stale.put("blockedByLegalHold", false);
    stale.put("warnings", new java.util.ArrayList<>());
    plan.put("targets", new java.util.ArrayList<>(List.of(stale)));

    Map<String, Object> search =
        Map.of(
            "targets",
            List.of(
                Map.of(
                    "targetKey",
                    "search.documents_stale",
                    "status",
                    "DRY_RUN_READY",
                    "eligibleCount",
                    88,
                    "purgeableCount",
                    88,
                    "blockedByLegalHold",
                    false,
                    "warnings",
                    List.of())));

    AdminPlatformRetentionProxyService.mergeSearchPlan(plan, search);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> targets = (List<Map<String, Object>>) plan.get("targets");
    assertThat(targets.get(0).get("eligibleCount")).isEqualTo(88);
    assertThat(targets.get(0).get("status")).isEqualTo("DRY_RUN_READY");
  }

  @Test
  void serviceFromKey_mapsWorkspaceAndSearchPrefixes() {
    assertThat(AdminPlatformRetentionProxyService.serviceFromKey("search.documents_stale"))
        .isEqualTo("search-service");
    assertThat(AdminPlatformRetentionProxyService.serviceFromKey("workspace.invitations_expired"))
        .isEqualTo("workspace-service");
  }

  @Test
  void mergeNotificationPlanOverridesNotificationTargetFields() {
    Map<String, Object> plan = new LinkedHashMap<>();
    Map<String, Object> fanoutSent = new LinkedHashMap<>();
    fanoutSent.put("targetKey", "notification.fanout_outbox_sent");
    fanoutSent.put("status", "INVENTORY_ONLY");
    fanoutSent.put("eligibleCount", null);
    fanoutSent.put("purgeableCount", 0);
    fanoutSent.put("blockedByLegalHold", false);
    fanoutSent.put("defaultRetentionDays", 7);
    fanoutSent.put("warnings", new java.util.ArrayList<>(List.of("Inventory only.")));
    Map<String, Object> identityUsers = new LinkedHashMap<>();
    identityUsers.put("targetKey", "identity.users");
    identityUsers.put("eligibleCount", null);
    plan.put("targets", new java.util.ArrayList<>(List.of(fanoutSent, identityUsers)));

    Map<String, Object> notification =
        Map.of(
            "targets",
            List.of(
                Map.of(
                    "targetKey",
                    "notification.fanout_outbox_sent",
                    "status",
                    "DRY_RUN_READY",
                    "eligibleCount",
                    340,
                    "purgeableCount",
                    340,
                    "blockedByLegalHold",
                    false,
                    "defaultRetentionDays",
                    7,
                    "cutoff",
                    "2026-05-09T00:00:00Z",
                    "warnings",
                    List.of("NOTIFICATION_RETENTION_QUERY_CAPPED"))));

    AdminPlatformRetentionProxyService.mergeNotificationPlan(plan, notification);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> targets = (List<Map<String, Object>>) plan.get("targets");
    Map<String, Object> merged = targets.get(0);
    assertThat(merged.get("status")).isEqualTo("DRY_RUN_READY");
    assertThat(merged.get("eligibleCount")).isEqualTo(340);
    assertThat(merged.get("purgeableCount")).isEqualTo(340);
    assertThat(merged.get("cutoff")).isEqualTo("2026-05-09T00:00:00Z");
    assertThat(merged.get("defaultRetentionDays")).isEqualTo(7);
    @SuppressWarnings("unchecked")
    List<Object> warnings = (List<Object>) merged.get("warnings");
    assertThat(warnings).contains("Inventory only.", "NOTIFICATION_RETENTION_QUERY_CAPPED");
    assertThat(targets.get(1).get("eligibleCount")).isNull();
  }

  @Test
  void mergeNotificationPlanLeavesNonNotificationTargetsUntouched() {
    Map<String, Object> plan = new LinkedHashMap<>();
    Map<String, Object> noteVersions = new LinkedHashMap<>();
    noteVersions.put("targetKey", "content.note_versions");
    noteVersions.put("status", "DRY_RUN_READY");
    noteVersions.put("eligibleCount", 1200);
    plan.put("targets", new java.util.ArrayList<>(List.of(noteVersions)));

    Map<String, Object> notification =
        Map.of(
            "targets",
            List.of(
                Map.of(
                    "targetKey",
                    "notification.analytics_hourly",
                    "status",
                    "DRY_RUN_READY",
                    "eligibleCount",
                    99)));

    AdminPlatformRetentionProxyService.mergeNotificationPlan(plan, notification);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> targets = (List<Map<String, Object>>) plan.get("targets");
    assertThat(targets).hasSize(1);
    assertThat(targets.get(0).get("status")).isEqualTo("DRY_RUN_READY");
    assertThat(targets.get(0).get("eligibleCount")).isEqualTo(1200);
  }

  @Test
  void mergeNotificationPlanDedupesWarnings() {
    Map<String, Object> plan = new LinkedHashMap<>();
    Map<String, Object> digest = new LinkedHashMap<>();
    digest.put("targetKey", "notification.digest_items_terminal");
    digest.put(
        "warnings", new java.util.ArrayList<>(List.of("NOTIFICATION_RETENTION_QUERY_CAPPED")));
    plan.put("targets", new java.util.ArrayList<>(List.of(digest)));

    Map<String, Object> notification =
        Map.of(
            "targets",
            List.of(
                Map.of(
                    "targetKey",
                    "notification.digest_items_terminal",
                    "warnings",
                    List.of("NOTIFICATION_RETENTION_QUERY_CAPPED"))));

    AdminPlatformRetentionProxyService.mergeNotificationPlan(plan, notification);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> targets = (List<Map<String, Object>>) plan.get("targets");
    @SuppressWarnings("unchecked")
    List<Object> warnings = (List<Object>) targets.get(0).get("warnings");
    assertThat(warnings).containsExactly("NOTIFICATION_RETENTION_QUERY_CAPPED");
  }

  @Test
  void addWarningDeduplicatesNotificationUnavailable() {
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("warnings", new java.util.ArrayList<>(List.of("X")));

    AdminPlatformRetentionProxyService.addWarning(
        plan, "NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE");
    AdminPlatformRetentionProxyService.addWarning(
        plan, "NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE");

    @SuppressWarnings("unchecked")
    List<Object> warnings = (List<Object>) plan.get("warnings");
    assertThat(warnings).containsExactly("X", "NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> summariesOf(Map<String, Object> plan) {
    AdminPlatformRetentionProxyService.applyServiceSummaries(plan);
    return (List<Map<String, Object>>) plan.get("serviceSummaries");
  }

  private static Map<String, Object> target(
      String targetKey, String service, String status, boolean blocked, List<String> warnings) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("targetKey", targetKey);
    row.put("service", service);
    row.put("status", status);
    row.put("blockedByLegalHold", blocked);
    row.put("warnings", new java.util.ArrayList<>(warnings));
    return row;
  }

  private static Map<String, Object> findSummary(
      List<Map<String, Object>> summaries, String service) {
    return summaries.stream()
        .filter(s -> service.equals(s.get("service")))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void serviceSummariesAggregateContentTargetsAsReady() {
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put(
        "targets",
        new java.util.ArrayList<>(
            List.of(
                target(
                    "content.note_versions", "content-service", "DRY_RUN_READY", false, List.of()),
                target("content.comments", "content-service", "DRY_RUN_READY", false, List.of()))));

    Map<String, Object> content = findSummary(summariesOf(plan), "content-service");

    assertThat(content.get("dataClass")).isEqualTo("CONTENT");
    assertThat(content.get("status")).isEqualTo("READY");
    assertThat(content.get("totalTargets")).isEqualTo(2);
    assertThat(content.get("dryRunReadyTargets")).isEqualTo(2);
    assertThat(content.get("inventoryOnlyTargets")).isEqualTo(0);
    assertThat(content.get("blockedTargets")).isEqualTo(0);
  }

  @Test
  void serviceSummaryPartialWhenMixedReadyAndInventory() {
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put(
        "targets",
        new java.util.ArrayList<>(
            List.of(
                target(
                    "notification.fanout_outbox_sent",
                    "notification-service",
                    "DRY_RUN_READY",
                    false,
                    List.of()),
                target(
                    "notification.notes",
                    "notification-service",
                    "INVENTORY_ONLY",
                    false,
                    List.of()))));

    Map<String, Object> n = findSummary(summariesOf(plan), "notification-service");

    assertThat(n.get("status")).isEqualTo("PARTIAL");
    assertThat(n.get("dryRunReadyTargets")).isEqualTo(1);
    assertThat(n.get("inventoryOnlyTargets")).isEqualTo(1);
  }

  @Test
  void serviceSummaryReflectsLegalHoldBlock() {
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put(
        "targets",
        new java.util.ArrayList<>(
            List.of(
                target(
                    "notification.fanout_outbox_sent",
                    "notification-service",
                    "DRY_RUN_READY",
                    true,
                    List.of("NOTIFICATION_RETENTION_LEGAL_HOLD_BLOCKED")))));

    Map<String, Object> n = findSummary(summariesOf(plan), "notification-service");

    assertThat(n.get("status")).isEqualTo("BLOCKED_BY_HOLD");
    assertThat(n.get("blockedTargets")).isEqualTo(1);
  }

  @Test
  void serviceSummaryReflectsServiceUnavailablePlanWarning() {
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put(
        "targets",
        new java.util.ArrayList<>(
            List.of(
                target(
                    "content.note_versions",
                    "content-service",
                    "INVENTORY_ONLY",
                    false,
                    List.of()))));
    plan.put(
        "warnings", new java.util.ArrayList<>(List.of("CONTENT_RETENTION_SERVICE_UNAVAILABLE")));

    Map<String, Object> content = findSummary(summariesOf(plan), "content-service");

    assertThat(content.get("status")).isEqualTo("UNAVAILABLE");
    @SuppressWarnings("unchecked")
    List<Object> contentWarnings = (List<Object>) content.get("warnings");
    assertThat(contentWarnings).contains("CONTENT_RETENTION_SERVICE_UNAVAILABLE");
  }

  @Test
  void serviceSummaryDedupesWarningsAndCountsCapped() {
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put(
        "targets",
        new java.util.ArrayList<>(
            List.of(
                target(
                    "notification.digest_items_terminal",
                    "notification-service",
                    "DRY_RUN_READY",
                    false,
                    List.of(
                        "NOTIFICATION_RETENTION_QUERY_CAPPED",
                        "NOTIFICATION_RETENTION_QUERY_CAPPED")))));
    plan.put(
        "warnings",
        new java.util.ArrayList<>(List.of("PLATFORM_RETENTION_NOTIFICATION_PLAN_INCLUDED")));

    Map<String, Object> n = findSummary(summariesOf(plan), "notification-service");

    @SuppressWarnings("unchecked")
    List<Object> warnings = (List<Object>) n.get("warnings");
    assertThat(warnings)
        .containsExactly(
            "NOTIFICATION_RETENTION_QUERY_CAPPED", "PLATFORM_RETENTION_NOTIFICATION_PLAN_INCLUDED");
    assertThat(n.get("warningCount")).isEqualTo(2);
    assertThat(n.get("cappedTargets")).isEqualTo(1);
  }

  @Test
  void applyServiceSummariesKeepsExistingTargetsContractIntact() {
    Map<String, Object> plan = new LinkedHashMap<>();
    Map<String, Object> noteVersions =
        target("content.note_versions", "content-service", "DRY_RUN_READY", false, List.of());
    noteVersions.put("eligibleCount", 1200);
    plan.put("targets", new java.util.ArrayList<>(List.of(noteVersions)));

    AdminPlatformRetentionProxyService.applyServiceSummaries(plan);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> targets = (List<Map<String, Object>>) plan.get("targets");
    assertThat(targets).hasSize(1);
    assertThat(targets.get(0).get("targetKey")).isEqualTo("content.note_versions");
    assertThat(targets.get(0).get("eligibleCount")).isEqualTo(1200);
    assertThat(plan).containsKey("serviceSummaries");
  }

  @Test
  void mergeContentPlanLeavesIdentityTargetsUntouched() {
    Map<String, Object> plan = new LinkedHashMap<>();
    Map<String, Object> users = new LinkedHashMap<>();
    users.put("targetKey", "identity.users");
    users.put("status", "INVENTORY_ONLY");
    users.put("eligibleCount", null);
    users.put("purgeableCount", 0);
    users.put("blockedByLegalHold", false);
    users.put("warnings", new java.util.ArrayList<>(List.of("Inventory only.")));
    Map<String, Object> scimRuns = new LinkedHashMap<>();
    scimRuns.put("targetKey", "identity.scim_sync_runs");
    scimRuns.put("status", "DRY_RUN_READY");
    scimRuns.put("eligibleCount", null);
    scimRuns.put("purgeableCount", 0);
    scimRuns.put("blockedByLegalHold", false);
    scimRuns.put("warnings", new java.util.ArrayList<>());
    plan.put("targets", new java.util.ArrayList<>(List.of(users, scimRuns)));

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
                    100)));

    AdminPlatformRetentionProxyService.mergeContentPlan(plan, content);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> targets = (List<Map<String, Object>>) plan.get("targets");
    assertThat(targets).hasSize(2);
    assertThat(targets.get(0).get("status")).isEqualTo("INVENTORY_ONLY");
    assertThat(targets.get(0).get("eligibleCount")).isNull();
    assertThat(targets.get(1).get("status")).isEqualTo("DRY_RUN_READY");
    assertThat(targets.get(1).get("eligibleCount")).isNull();
  }
}

package com.notebook.lumen.identity.admin.changerequest;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AdminOperationRegistry {
  public static final String OP_ADMIN_MFA_MODE_UPDATE = "ADMIN_MFA_MODE_UPDATE";
  public static final String OP_MERGE_ANALYSIS_ROLLOUT_REQUEST = "MERGE_ANALYSIS_ROLLOUT_REQUEST";
  public static final String OP_MERGE_APPLY_ROLLOUT_REQUEST = "MERGE_APPLY_ROLLOUT_REQUEST";
  public static final String OP_SCIM_BULK_ROLLOUT_REQUEST = "SCIM_BULK_ROLLOUT_REQUEST";

  private static final Map<String, AdminOperationDefinition> BY_TYPE =
      Map.ofEntries(
          Map.entry(
              OP_ADMIN_MFA_MODE_UPDATE,
              new AdminOperationDefinition(
                  OP_ADMIN_MFA_MODE_UPDATE,
                  "api-gateway",
                  "GATEWAY_ADMIN_MFA_MODE",
                  Set.of("off", "observe", "warn", "enforce"),
                  "HIGH",
                  PlatformAdminRbacConstants.PERM_SECURITY_CHANGE_REQUEST_CREATE,
                  true,
                  false,
                  "Revert via GitOps to prior GATEWAY_ADMIN_MFA_MODE or submit a change request with a lower mode.",
                  List.of("/admin/**", "Admin audit & enterprise console"),
                  "Gateway admin MFA policy mode (off/observe/warn/enforce).")),
          Map.entry(
              OP_MERGE_ANALYSIS_ROLLOUT_REQUEST,
              new AdminOperationDefinition(
                  OP_MERGE_ANALYSIS_ROLLOUT_REQUEST,
                  "content-service",
                  "NOTE_MERGE_ANALYSIS_ENABLED",
                  Set.of("true", "false"),
                  "MEDIUM",
                  PlatformAdminRbacConstants.PERM_MERGE_CHANGE_REQUEST_CREATE,
                  true,
                  false,
                  "Set NOTE_MERGE_ANALYSIS_ENABLED to the previous boolean in GitOps.",
                  List.of("POST /notes/{id}/merge/analyze"),
                  "Backend merge analysis feature flag.")),
          Map.entry(
              OP_MERGE_APPLY_ROLLOUT_REQUEST,
              new AdminOperationDefinition(
                  OP_MERGE_APPLY_ROLLOUT_REQUEST,
                  "content-service",
                  "NOTE_MERGE_APPLY_ENABLED",
                  Set.of("true", "false"),
                  "HIGH",
                  PlatformAdminRbacConstants.PERM_MERGE_CHANGE_REQUEST_CREATE,
                  true,
                  false,
                  "Set NOTE_MERGE_APPLY_ENABLED to false in GitOps if rollback needed.",
                  List.of("POST /notes/{id}/merge/apply"),
                  "Backend merge apply feature flag.")),
          Map.entry(
              OP_SCIM_BULK_ROLLOUT_REQUEST,
              new AdminOperationDefinition(
                  OP_SCIM_BULK_ROLLOUT_REQUEST,
                  "identity-service",
                  "SCIM_BULK_ENABLED",
                  Set.of("true", "false"),
                  "HIGH",
                  PlatformAdminRbacConstants.PERM_SCIM_CHANGE_REQUEST_CREATE,
                  true,
                  false,
                  "Disable SCIM_BULK_ENABLED in GitOps; bulk is non-transactional.",
                  List.of("POST /scim/v2/Bulk"),
                  "SCIM bulk operations enablement.")));

  public Optional<AdminOperationDefinition> find(String operationType) {
    if (operationType == null || operationType.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_TYPE.get(operationType.trim()));
  }

  public List<AdminOperationDefinition> all() {
    return BY_TYPE.values().stream().toList();
  }

  public String normalizeValue(AdminOperationDefinition def, String requestedValue) {
    if (requestedValue == null) {
      return "";
    }
    String v = requestedValue.trim().toLowerCase(Locale.ROOT);
    if (def.allowedNormalizedValues().contains("true") || def.allowedNormalizedValues().contains("false")) {
      if ("1".equals(v) || "yes".equals(v)) {
        return "true";
      }
      if ("0".equals(v) || "no".equals(v)) {
        return "false";
      }
    }
    return v;
  }

  public Map<String, Object> toImpactSummary(AdminOperationDefinition def) {
    return Map.of(
        "severity",
        def.severity(),
        "description",
        def.description(),
        "rollback",
        def.rollbackHint(),
        "affectedSurfaces",
        def.affectedSurfaces(),
        "requiresApproval",
        def.requiresApproval(),
        "runtimeApplySupported",
        def.runtimeApplySupported(),
        "targetService",
        def.targetService(),
        "targetKey",
        def.targetKey());
  }

  public Map<String, Object> validationResult(boolean valid, AdminOperationDefinition def) {
    return Map.of(
        "valid",
        valid,
        "requiresApproval",
        def.requiresApproval(),
        "operationType",
        def.operationType());
  }

  public static String allowedValuesHint(AdminOperationDefinition def) {
    return def.allowedNormalizedValues().stream().sorted().collect(Collectors.joining(", "));
  }
}

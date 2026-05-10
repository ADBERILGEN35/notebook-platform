package com.notebook.lumen.identity.admin.gitops;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Allow-listed change-request operation → single key under {@code config} in GitOps values.yaml. */
public enum GitOpsPathMapping {
  ADMIN_MFA_MODE_UPDATE("ADMIN_MFA_MODE_UPDATE", "gatewayAdminMfaMode"),
  MERGE_ANALYSIS_ROLLOUT_REQUEST("MERGE_ANALYSIS_ROLLOUT_REQUEST", "noteMergeAnalysisEnabled"),
  MERGE_APPLY_ROLLOUT_REQUEST("MERGE_APPLY_ROLLOUT_REQUEST", "noteMergeApplyEnabled"),
  SCIM_BULK_ROLLOUT_REQUEST("SCIM_BULK_ROLLOUT_REQUEST", "scimBulkEnabled");

  private final String operationType;
  private final String configKey;

  GitOpsPathMapping(String operationType, String configKey) {
    this.operationType = operationType;
    this.configKey = configKey;
  }

  public String operationType() {
    return operationType;
  }

  public String configKey() {
    return configKey;
  }

  public static Optional<GitOpsPathMapping> forOperation(String operationType) {
    if (operationType == null || operationType.isBlank()) {
      return Optional.empty();
    }
    String n = operationType.trim();
    return Arrays.stream(values()).filter(m -> m.operationType.equals(n)).findFirst();
  }

  public static String relativeValuesFile(String environment) {
    String env = environment.trim().toLowerCase(Locale.ROOT);
    return "deploy/gitops/environments/" + env + "/values.yaml";
  }

  /** Dot path under document root (config is top-level in values.yaml). */
  public String yamlDotPath() {
    return "config." + configKey;
  }
}

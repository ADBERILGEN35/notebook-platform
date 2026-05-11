package com.notebook.lumen.identity.admin.gitops;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Allow-listed change-request operation → GitOps file path and patch semantics.
 *
 * <p>Scalar config keys live under {@code config.*} in {@code values.yaml}. Admin RBAC proposals
 * append rows to {@code admin-rbac-overrides.yaml} (governance artifact; not runtime-ingested in
 * Faz 87).
 */
public enum GitOpsPathMapping {
  ADMIN_MFA_MODE_UPDATE("ADMIN_MFA_MODE_UPDATE", Optional.of("gatewayAdminMfaMode")),
  MERGE_ANALYSIS_ROLLOUT_REQUEST(
      "MERGE_ANALYSIS_ROLLOUT_REQUEST", Optional.of("noteMergeAnalysisEnabled")),
  MERGE_APPLY_ROLLOUT_REQUEST("MERGE_APPLY_ROLLOUT_REQUEST", Optional.of("noteMergeApplyEnabled")),
  SCIM_BULK_ROLLOUT_REQUEST("SCIM_BULK_ROLLOUT_REQUEST", Optional.of("scimBulkEnabled")),
  ADMIN_RBAC_ROLE_GRANT_REQUEST("ADMIN_RBAC_ROLE_GRANT_REQUEST", Optional.empty()),
  ADMIN_RBAC_ROLE_REVOKE_REQUEST("ADMIN_RBAC_ROLE_REVOKE_REQUEST", Optional.empty());

  private final String operationType;
  private final Optional<String> valuesYamlConfigKey;

  GitOpsPathMapping(String operationType, Optional<String> valuesYamlConfigKey) {
    this.operationType = operationType;
    this.valuesYamlConfigKey = valuesYamlConfigKey;
  }

  public String operationType() {
    return operationType;
  }

  /** Present for {@code values.yaml} scalar patches; empty for admin RBAC override manifests. */
  public Optional<String> valuesYamlConfigKey() {
    return valuesYamlConfigKey;
  }

  public boolean isAdminRbacOverridesFile() {
    return valuesYamlConfigKey.isEmpty();
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

  public static String relativeAdminRbacOverridesFile(String environment) {
    String env = environment.trim().toLowerCase(Locale.ROOT);
    return "deploy/gitops/environments/" + env + "/admin-rbac-overrides.yaml";
  }

  public String relativePath(String environment) {
    if (isAdminRbacOverridesFile()) {
      return relativeAdminRbacOverridesFile(environment);
    }
    return relativeValuesFile(environment);
  }

  /** Dot path for UI preview; RBAC uses a synthetic path for list append. */
  public String yamlDotPath() {
    return valuesYamlConfigKey.map(k -> "config." + k).orElse("adminRbacOverrides.assignments[+]");
  }

  public String configKey() {
    return valuesYamlConfigKey.orElseThrow(
        () -> new IllegalStateException("configKey is only defined for values.yaml mappings"));
  }
}

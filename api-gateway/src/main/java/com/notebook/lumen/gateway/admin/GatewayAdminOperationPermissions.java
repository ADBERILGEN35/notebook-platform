package com.notebook.lumen.gateway.admin;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import java.util.Map;
import java.util.Optional;

/** Mirrors identity-service AdminOperationRegistry operation → required create permission. */
public final class GatewayAdminOperationPermissions {

  private static final Map<String, String> BY_OPERATION =
      Map.of(
          "ADMIN_MFA_MODE_UPDATE", PlatformAdminRbacConstants.PERM_SECURITY_CHANGE_REQUEST_CREATE,
          "MERGE_ANALYSIS_ROLLOUT_REQUEST",
              PlatformAdminRbacConstants.PERM_MERGE_CHANGE_REQUEST_CREATE,
          "MERGE_APPLY_ROLLOUT_REQUEST",
              PlatformAdminRbacConstants.PERM_MERGE_CHANGE_REQUEST_CREATE,
          "SCIM_BULK_ROLLOUT_REQUEST", PlatformAdminRbacConstants.PERM_SCIM_CHANGE_REQUEST_CREATE,
          "ADMIN_RBAC_ROLE_GRANT_REQUEST",
              PlatformAdminRbacConstants.PERM_RBAC_CHANGE_REQUEST_CREATE,
          "ADMIN_RBAC_ROLE_REVOKE_REQUEST",
              PlatformAdminRbacConstants.PERM_RBAC_CHANGE_REQUEST_CREATE);

  private GatewayAdminOperationPermissions() {}

  public static Optional<String> requiredCreatePermission(String operationType) {
    if (operationType == null || operationType.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_OPERATION.get(operationType.trim()));
  }
}

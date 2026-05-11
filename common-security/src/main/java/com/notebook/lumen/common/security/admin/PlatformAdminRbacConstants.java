package com.notebook.lumen.common.security.admin;

import java.util.List;
import java.util.Set;

/** Shared admin RBAC identifiers for identity-service and api-gateway. */
public final class PlatformAdminRbacConstants {

  private PlatformAdminRbacConstants() {}

  public static List<String> allPermissions() {
    return List.of(
        PERM_AUDIT_READ,
        PERM_AUDIT_EXPORT,
        PERM_ENTERPRISE_STATUS_READ,
        PERM_CHANGE_REQUEST_LIST,
        PERM_CHANGE_REQUEST_CREATE,
        PERM_CHANGE_REQUEST_APPROVE,
        PERM_CHANGE_REQUEST_REJECT,
        PERM_CHANGE_REQUEST_CANCEL,
        PERM_SECURITY_CHANGE_REQUEST_CREATE,
        PERM_MERGE_CHANGE_REQUEST_CREATE,
        PERM_SCIM_CHANGE_REQUEST_CREATE,
        PERM_SIEM_CHANGE_REQUEST_CREATE,
        PERM_IDENTITY_READ,
        PERM_RBAC_READ,
        PERM_RBAC_CHANGE_REQUEST_CREATE,
        PERM_CHANGE_REQUEST_GITOPS_DRY_RUN,
        PERM_CHANGE_REQUEST_GITOPS_CREATE,
        PERM_NOTIFICATIONS_ANALYTICS_READ,
        PERM_NOTIFICATIONS_DEAD_LETTER_READ,
        PERM_NOTIFICATIONS_DEAD_LETTER_REQUEUE,
        PERM_NOTIFICATIONS_RETENTION_READ,
        PERM_NOTIFICATIONS_RETENTION_RUN,
        PERM_NOTIFICATIONS_LEGAL_HOLD_READ,
        PERM_NOTIFICATIONS_LEGAL_HOLD_WRITE);
  }

  /**
   * Roles that may appear in admin RBAC change requests (grant/revoke requests; no runtime apply).
   */
  public static Set<String> assignableAdminRoles() {
    return Set.of(
        ROLE_PLATFORM_ADMIN,
        ROLE_PLATFORM_AUDIT_VIEWER,
        ROLE_PLATFORM_AUDIT_EXPORTER,
        ROLE_PLATFORM_SECURITY_ADMIN,
        ROLE_PLATFORM_IDENTITY_ADMIN,
        ROLE_PLATFORM_CHANGE_REQUEST_AUTHOR,
        ROLE_PLATFORM_CHANGE_REQUEST_APPROVER,
        ROLE_PLATFORM_OBSERVABILITY_VIEWER);
  }

  public static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
  public static final String ROLE_PLATFORM_AUDIT_VIEWER = "PLATFORM_AUDIT_VIEWER";
  public static final String ROLE_PLATFORM_AUDIT_EXPORTER = "PLATFORM_AUDIT_EXPORTER";
  public static final String ROLE_PLATFORM_SECURITY_ADMIN = "PLATFORM_SECURITY_ADMIN";
  public static final String ROLE_PLATFORM_IDENTITY_ADMIN = "PLATFORM_IDENTITY_ADMIN";
  public static final String ROLE_PLATFORM_CHANGE_REQUEST_AUTHOR = "PLATFORM_CHANGE_REQUEST_AUTHOR";
  public static final String ROLE_PLATFORM_CHANGE_REQUEST_APPROVER =
      "PLATFORM_CHANGE_REQUEST_APPROVER";
  public static final String ROLE_PLATFORM_OBSERVABILITY_VIEWER = "PLATFORM_OBSERVABILITY_VIEWER";

  public static final String PERM_AUDIT_READ = "admin:audit:read";
  public static final String PERM_AUDIT_EXPORT = "admin:audit:export";
  public static final String PERM_ENTERPRISE_STATUS_READ = "admin:enterprise:status:read";
  public static final String PERM_CHANGE_REQUEST_LIST = "admin:change-request:list";
  public static final String PERM_CHANGE_REQUEST_CREATE = "admin:change-request:create";
  public static final String PERM_CHANGE_REQUEST_APPROVE = "admin:change-request:approve";
  public static final String PERM_CHANGE_REQUEST_REJECT = "admin:change-request:reject";
  public static final String PERM_CHANGE_REQUEST_CANCEL = "admin:change-request:cancel";
  public static final String PERM_SECURITY_CHANGE_REQUEST_CREATE =
      "admin:security:change-request:create";
  public static final String PERM_MERGE_CHANGE_REQUEST_CREATE = "admin:merge:change-request:create";
  public static final String PERM_SCIM_CHANGE_REQUEST_CREATE = "admin:scim:change-request:create";
  public static final String PERM_SIEM_CHANGE_REQUEST_CREATE = "admin:siem:change-request:create";
  public static final String PERM_IDENTITY_READ = "admin:identity:read";
  public static final String PERM_RBAC_READ = "admin:rbac:read";
  public static final String PERM_RBAC_CHANGE_REQUEST_CREATE = "admin:rbac:change-request:create";
  public static final String PERM_CHANGE_REQUEST_GITOPS_DRY_RUN =
      "admin:change-request:gitops:dry-run";
  public static final String PERM_CHANGE_REQUEST_GITOPS_CREATE =
      "admin:change-request:gitops:create";
  public static final String PERM_NOTIFICATIONS_ANALYTICS_READ =
      "admin:notifications:analytics:read";
  public static final String PERM_NOTIFICATIONS_DEAD_LETTER_READ =
      "admin:notifications:dead-letter:read";
  public static final String PERM_NOTIFICATIONS_DEAD_LETTER_REQUEUE =
      "admin:notifications:dead-letter:requeue";
  public static final String PERM_NOTIFICATIONS_RETENTION_READ =
      "admin:notifications:retention:read";
  public static final String PERM_NOTIFICATIONS_RETENTION_RUN = "admin:notifications:retention:run";
  public static final String PERM_NOTIFICATIONS_LEGAL_HOLD_READ =
      "admin:notifications:legal-hold:read";
  public static final String PERM_NOTIFICATIONS_LEGAL_HOLD_WRITE =
      "admin:notifications:legal-hold:write";
}

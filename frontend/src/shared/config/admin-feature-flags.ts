export type AuditApiMode = 'mock' | 'real'

/** Runtime config may stringify booleans in HTML; allow `boolean` for type safety. */
const parseBool = (raw: string | boolean | undefined, defaultValue = false): boolean => {
  if (raw === undefined || raw === null || raw === '') return defaultValue
  if (typeof raw === 'boolean') return raw
  return String(raw).toLowerCase() === 'true' || raw === '1'
}

export const isAdminUiEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.ADMIN_UI_ENABLED ?? import.meta.env.VITE_ADMIN_UI_ENABLED,
    false,
  )

export const isAdminUiDevOpen = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.ADMIN_UI_DEV_OPEN ?? import.meta.env.VITE_ADMIN_UI_DEV_OPEN,
    false,
  )

/** Faz 77: enterprise admin change requests (validate + create PENDING only; no live config mutation). */
export const isEnterpriseAdminWriteEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.ENTERPRISE_ADMIN_WRITE_ENABLED ??
      import.meta.env.VITE_ENTERPRISE_ADMIN_WRITE_ENABLED,
    false,
  )

/** Faz 78: approve/reject controls on change requests (defaults on when enterprise write is enabled). */
export const isEnterpriseAdminApprovalsUiEnabled = (): boolean =>
  isEnterpriseAdminWriteEnabled() &&
  parseBool(
    window.__NOTEBOOK_CONFIG__?.ENTERPRISE_ADMIN_APPROVALS_ENABLED ??
      import.meta.env.VITE_ENTERPRISE_ADMIN_APPROVALS_ENABLED,
    true,
  )

/** Faz 80: GitOps PR dry-run / create for APPROVED change requests (no runtime apply). */
export const isEnterpriseGitOpsPrUiEnabled = (): boolean =>
  isEnterpriseAdminWriteEnabled() &&
  parseBool(
    window.__NOTEBOOK_CONFIG__?.ENTERPRISE_GITOPS_PR_ENABLED ??
      import.meta.env.VITE_ENTERPRISE_GITOPS_PR_ENABLED,
    false,
  )

/**
 * Faz 87: GitOps dry-run / PR for approved admin RBAC role grant/revoke requests (manifest proposal only).
 * Requires {@link isEnterpriseGitOpsPrUiEnabled} plus explicit opt-in.
 */
export const isEnterpriseGitOpsRbacRoleRequestsUiEnabled = (): boolean =>
  isEnterpriseGitOpsPrUiEnabled() &&
  parseBool(
    window.__NOTEBOOK_CONFIG__?.GITOPS_RBAC_ROLE_REQUESTS_ENABLED ??
      import.meta.env.VITE_GITOPS_RBAC_ROLE_REQUESTS_ENABLED,
    false,
  )

/** Faz 81: notification delivery analytics admin dashboard (aggregate-only). */
export const isNotificationAnalyticsUiEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.NOTIFICATION_ANALYTICS_UI_ENABLED ??
      import.meta.env.VITE_NOTIFICATION_ANALYTICS_UI_ENABLED,
    false,
  )

/** Faz 82: fanout dead-letter list / dry-run / requeue (no raw payloads). */
export const isNotificationDeadLetterUiEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.NOTIFICATION_DEAD_LETTER_UI_ENABLED ??
      import.meta.env.VITE_NOTIFICATION_DEAD_LETTER_UI_ENABLED,
    false,
  )

/** Faz 83: retention plan / dry-run UI (no raw notification content). */
export const isNotificationRetentionUiEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.NOTIFICATION_RETENTION_UI_ENABLED ??
      import.meta.env.VITE_NOTIFICATION_RETENTION_UI_ENABLED,
    false,
  )

/**
 * Shows destructive manual purge controls; server still requires NOTIFICATION_RETENTION_MANUAL_RUN_ENABLED
 * and MFA when applicable.
 */
export const isNotificationRetentionPurgeUiEnabled = (): boolean =>
  isNotificationRetentionUiEnabled() &&
  parseBool(
    window.__NOTEBOOK_CONFIG__?.NOTIFICATION_RETENTION_PURGE_UI_ENABLED ??
      import.meta.env.VITE_NOTIFICATION_RETENTION_PURGE_UI_ENABLED,
    false,
  )

/** Faz 84: notification-scoped legal holds (retention governance). */
export const isNotificationLegalHoldUiEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.NOTIFICATION_LEGAL_HOLD_UI_ENABLED ??
      import.meta.env.VITE_NOTIFICATION_LEGAL_HOLD_UI_ENABLED,
    false,
  )

/** Faz 86: admin RBAC directory (read-only via gateway). */
export const isAdminRbacUiEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.ADMIN_RBAC_UI_ENABLED ?? import.meta.env.VITE_ADMIN_RBAC_UI_ENABLED,
    false,
  )

/** Faz 86: role grant/revoke change requests (no runtime apply). */
export const isAdminRbacRoleRequestsUiEnabled = (): boolean =>
  isAdminRbacUiEnabled() &&
  parseBool(
    window.__NOTEBOOK_CONFIG__?.ADMIN_RBAC_ROLE_REQUESTS_ENABLED ??
      import.meta.env.VITE_ADMIN_RBAC_ROLE_REQUESTS_ENABLED,
    false,
  )

export const getAuditApiMode = (): AuditApiMode => {
  const raw =
    window.__NOTEBOOK_CONFIG__?.AUDIT_API_MODE ?? import.meta.env.VITE_AUDIT_API_MODE ?? 'mock'
  const normalized = String(raw).toLowerCase()
  return normalized === 'real' ? 'real' : 'mock'
}

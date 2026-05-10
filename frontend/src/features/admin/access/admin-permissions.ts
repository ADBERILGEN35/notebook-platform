import type { AuthUser } from '../../../shared/types/api'

/** Mirrors api-gateway / identity JWT `platform_permissions` and operation checks. */
export const PERM_AUDIT_READ = 'admin:audit:read'
export const PERM_AUDIT_EXPORT = 'admin:audit:export'
export const PERM_ENTERPRISE_STATUS_READ = 'admin:enterprise:status:read'
export const PERM_CHANGE_REQUEST_LIST = 'admin:change-request:list'
export const PERM_CHANGE_REQUEST_CREATE = 'admin:change-request:create'
export const PERM_CHANGE_REQUEST_APPROVE = 'admin:change-request:approve'
export const PERM_CHANGE_REQUEST_REJECT = 'admin:change-request:reject'
export const PERM_CHANGE_REQUEST_CANCEL = 'admin:change-request:cancel'
export const PERM_SECURITY_CHANGE_REQUEST_CREATE = 'admin:security:change-request:create'
export const PERM_MERGE_CHANGE_REQUEST_CREATE = 'admin:merge:change-request:create'
export const PERM_SCIM_CHANGE_REQUEST_CREATE = 'admin:scim:change-request:create'
export const PERM_CHANGE_REQUEST_GITOPS_DRY_RUN = 'admin:change-request:gitops:dry-run'
export const PERM_CHANGE_REQUEST_GITOPS_CREATE = 'admin:change-request:gitops:create'
export const PERM_NOTIFICATIONS_ANALYTICS_READ = 'admin:notifications:analytics:read'
export const PERM_NOTIFICATIONS_DEAD_LETTER_READ = 'admin:notifications:dead-letter:read'
export const PERM_NOTIFICATIONS_DEAD_LETTER_REQUEUE = 'admin:notifications:dead-letter:requeue'
export const PERM_NOTIFICATIONS_RETENTION_READ = 'admin:notifications:retention:read'
export const PERM_NOTIFICATIONS_RETENTION_RUN = 'admin:notifications:retention:run'
export const PERM_NOTIFICATIONS_LEGAL_HOLD_READ = 'admin:notifications:legal-hold:read'
export const PERM_NOTIFICATIONS_LEGAL_HOLD_WRITE = 'admin:notifications:legal-hold:write'

const OP_TO_CREATE_PERM: Record<string, string> = {
  ADMIN_MFA_MODE_UPDATE: PERM_SECURITY_CHANGE_REQUEST_CREATE,
  MERGE_ANALYSIS_ROLLOUT_REQUEST: PERM_MERGE_CHANGE_REQUEST_CREATE,
  MERGE_APPLY_ROLLOUT_REQUEST: PERM_MERGE_CHANGE_REQUEST_CREATE,
  SCIM_BULK_ROLLOUT_REQUEST: PERM_SCIM_CHANGE_REQUEST_CREATE,
}

export function isPlatformAdmin(user: AuthUser | null): boolean {
  if (!user?.roles?.length) return false
  return user.roles.some((r) => {
    const x = String(r).toUpperCase()
    return x === 'PLATFORM_ADMIN' || x === 'ROLE_PLATFORM_ADMIN' || x === 'ADMIN' || x === 'ROLE_ADMIN'
  })
}

export function hasPlatformPermission(user: AuthUser | null, permission: string): boolean {
  if (!user) return false
  if (isPlatformAdmin(user)) return true
  return user.platformPermissions?.includes(permission) ?? false
}

export function hasAnyPlatformPermission(user: AuthUser | null, permissions: string[]): boolean {
  if (!user || permissions.length === 0) return false
  if (isPlatformAdmin(user)) return true
  const set = new Set(user.platformPermissions ?? [])
  return permissions.some((p) => set.has(p))
}

export function requiredCreatePermissionForOperation(operationType: string): string | undefined {
  return OP_TO_CREATE_PERM[operationType]
}

export function canCreateChangeRequestForOperation(user: AuthUser | null, operationType: string): boolean {
  if (!hasPlatformPermission(user, PERM_CHANGE_REQUEST_CREATE)) return false
  const opPerm = requiredCreatePermissionForOperation(operationType)
  if (!opPerm) return false
  return hasPlatformPermission(user, opPerm)
}

import type { AuthUser } from '../../../shared/types/api'
import {
  getAuditApiMode,
  isAdminUiDevOpen,
  isAdminUiEnabled,
} from '../../../shared/config/admin-feature-flags'

const ADMIN_LIKE_ROLES = new Set(['ADMIN', 'ROLE_ADMIN', 'PLATFORM_ADMIN'])

export function hasPlatformAdminLikeRole(user: AuthUser | null): boolean {
  if (!user?.roles?.length) return false
  return user.roles.some((r) => ADMIN_LIKE_ROLES.has(String(r)))
}

function hasPlatformPrefixedRole(user: AuthUser | null): boolean {
  if (!user?.roles?.length) return false
  return user.roles.some((r) => String(r).startsWith('PLATFORM_'))
}

export function canShowAdminNavigation(user: AuthUser | null): boolean {
  if (!isAdminUiEnabled()) return false
  if (isAdminUiDevOpen() && getAuditApiMode() === 'mock') return true
  if (hasPlatformAdminLikeRole(user)) return true
  if ((user?.platformPermissions?.length ?? 0) > 0) return true
  return hasPlatformPrefixedRole(user)
}

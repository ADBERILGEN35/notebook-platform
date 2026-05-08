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

export function canShowAdminNavigation(user: AuthUser | null): boolean {
  if (!isAdminUiEnabled()) return false
  if (isAdminUiDevOpen() && getAuditApiMode() === 'mock') return true
  return hasPlatformAdminLikeRole(user)
}

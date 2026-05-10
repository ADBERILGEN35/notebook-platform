import { describe, expect, it } from 'vitest'
import type { AuthUser } from '../../../shared/types/api'
import {
  PERM_AUDIT_READ,
  PERM_CHANGE_REQUEST_CREATE,
  PERM_MERGE_CHANGE_REQUEST_CREATE,
  canCreateChangeRequestForOperation,
  hasAnyPlatformPermission,
  hasPlatformPermission,
  isPlatformAdmin,
} from './admin-permissions'

const base: AuthUser = { id: 'u1', email: 'a@b.com', name: 'Ada', roles: ['ROLE_USER'] }

describe('admin-permissions', () => {
  it('isPlatformAdmin detects platform admin role', () => {
    expect(isPlatformAdmin({ ...base, roles: ['ROLE_USER', 'PLATFORM_ADMIN'] })).toBe(true)
    expect(isPlatformAdmin(base)).toBe(false)
  })

  it('hasPlatformPermission grants all for platform admin', () => {
    expect(hasPlatformPermission({ ...base, roles: ['PLATFORM_ADMIN'] }, PERM_AUDIT_READ)).toBe(true)
  })

  it('hasPlatformPermission uses JWT-derived permissions', () => {
    const u: AuthUser = {
      ...base,
      roles: ['ROLE_USER', 'PLATFORM_AUDIT_VIEWER'],
      platformPermissions: [PERM_AUDIT_READ],
    }
    expect(hasPlatformPermission(u, PERM_AUDIT_READ)).toBe(true)
    expect(hasPlatformPermission(u, PERM_CHANGE_REQUEST_CREATE)).toBe(false)
  })

  it('hasAnyPlatformPermission', () => {
    const u: AuthUser = { ...base, platformPermissions: [PERM_AUDIT_READ] }
    expect(hasAnyPlatformPermission(u, [PERM_CHANGE_REQUEST_CREATE, PERM_AUDIT_READ])).toBe(true)
    expect(hasAnyPlatformPermission(u, [PERM_CHANGE_REQUEST_CREATE])).toBe(false)
  })

  it('canCreateChangeRequestForOperation requires base + operation permission', () => {
    const author: AuthUser = {
      ...base,
      platformPermissions: [PERM_CHANGE_REQUEST_CREATE, PERM_MERGE_CHANGE_REQUEST_CREATE],
    }
    expect(canCreateChangeRequestForOperation(author, 'MERGE_APPLY_ROLLOUT_REQUEST')).toBe(true)
    expect(canCreateChangeRequestForOperation(author, 'ADMIN_MFA_MODE_UPDATE')).toBe(false)
  })
})

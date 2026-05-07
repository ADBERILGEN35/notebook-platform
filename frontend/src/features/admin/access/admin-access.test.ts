import { describe, expect, it } from 'vitest'
import { hasPlatformAdminLikeRole } from './admin-access'
import type { AuthUser } from '../../../shared/types/api'

describe('admin access helpers', () => {
  const base: AuthUser = { id: 'u1', email: 'a@b.com', name: 'Ada', roles: ['ROLE_USER'] }

  it('detects privileged roles', () => {
    expect(hasPlatformAdminLikeRole({ ...base, roles: ['PLATFORM_ADMIN'] })).toBe(true)
    expect(hasPlatformAdminLikeRole({ ...base, roles: ['ROLE_ADMIN'] })).toBe(true)
    expect(hasPlatformAdminLikeRole(base)).toBe(false)
    expect(hasPlatformAdminLikeRole({ ...base, roles: undefined })).toBe(false)
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { canShowAdminNavigation, hasPlatformAdminLikeRole } from './admin-access'
import type { AuthUser } from '../../../shared/types/api'

const configState = vi.hoisted(() => ({
  enabled: true,
  devOpen: false,
  mode: 'real' as 'mock' | 'real',
}))

vi.mock('../../../shared/config/admin-feature-flags', () => ({
  isAdminUiEnabled: () => configState.enabled,
  isAdminUiDevOpen: () => configState.devOpen,
  getAuditApiMode: () => configState.mode,
}))

describe('admin access helpers', () => {
  const base: AuthUser = { id: 'u1', email: 'a@b.com', name: 'Ada', roles: ['ROLE_USER'] }

  beforeEach(() => {
    configState.enabled = true
    configState.devOpen = false
    configState.mode = 'real'
  })

  it('detects privileged roles', () => {
    expect(hasPlatformAdminLikeRole({ ...base, roles: ['PLATFORM_ADMIN'] })).toBe(true)
    expect(hasPlatformAdminLikeRole({ ...base, roles: ['ROLE_ADMIN'] })).toBe(true)
    expect(hasPlatformAdminLikeRole(base)).toBe(false)
    expect(hasPlatformAdminLikeRole({ ...base, roles: undefined })).toBe(false)
  })

  it('canShowAdminNavigation allows fine-grained platform roles', () => {
    expect(
      canShowAdminNavigation({
        ...base,
        roles: ['ROLE_USER', 'PLATFORM_AUDIT_VIEWER'],
      }),
    ).toBe(true)
  })

  it('allows admin navigation for privileged role', () => {
    expect(canShowAdminNavigation({ ...base, roles: ['PLATFORM_ADMIN'] })).toBe(true)
  })

  it('allows dev-open only in mock mode', () => {
    configState.devOpen = true
    configState.mode = 'mock'
    expect(canShowAdminNavigation(base)).toBe(true)

    configState.mode = 'real'
    expect(canShowAdminNavigation(base)).toBe(false)
  })
})

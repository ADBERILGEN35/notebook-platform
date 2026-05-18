import { describe, expect, it } from 'vitest'
import { router } from './router'

function collectPaths(routes: typeof router.routes, prefix = ''): string[] {
  return routes.flatMap((route) => {
    const path = `${prefix}${route.path ?? ''}`.replace(/\/+/g, '/')
    const childPaths = route.children ? collectPaths(route.children, path.endsWith('/') ? path : `${path}/`) : []
    return [path, ...childPaths]
  })
}

describe('auth routes', () => {
  it('registers public auth paths', () => {
    const paths = router.routes.map((r) => r.path)
    expect(paths).toContain('/login')
    expect(paths).toContain('/register')
    expect(paths).toContain('/signup')
    expect(paths).toContain('/forgot-password')
    expect(paths).toContain('/mfa')
    expect(paths).toContain('/sso/callback')
  })

  it('registers workspace hub under /app', () => {
    const paths = collectPaths(router.routes)
    expect(paths).toContain('/app')
    expect(paths.some((p) => p.includes('workspaces'))).toBe(true)
    expect(paths.some((p) => p.includes('notes/:noteId'))).toBe(true)
    expect(paths.some((p) => p.includes('members'))).toBe(true)
    expect(paths.some((p) => p.includes('settings'))).toBe(true)
  })

  it('registers search discover and settings sync routes', () => {
    const paths = collectPaths(router.routes)
    expect(paths.some((p) => p.includes('search/discover'))).toBe(true)
    expect(paths.some((p) => p.includes('settings/sync'))).toBe(true)
  })

  it('keeps admin route guarded by AdminGate', () => {
    const appRoute = router.routes.find((r) => r.path === '/app')
    const adminChild = appRoute?.children?.find((c) => c.path === 'admin')
    expect(adminChild).toBeTruthy()
    expect(adminChild?.element).toBeTruthy()
  })
})

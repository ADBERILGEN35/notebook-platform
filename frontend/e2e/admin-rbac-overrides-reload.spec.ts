import { test, expect } from '@playwright/test'
import { signUpAndLogin } from './helpers/auth.helper'
import { stubAdminRbacSession } from './helpers/stub-admin-rbac-session'
import { matchGatewayAdminRbacApi } from './helpers/gateway-admin-rbac-urls'

test.beforeEach(async ({ page }) => {
  await stubAdminRbacSession(page, 'rbac-override-reloader')
})

test.describe('Admin RBAC overrides reload (Faz 89)', () => {
  test('reload flow succeeds when stubbed', async ({ page }) => {
    await page.route(matchGatewayAdminRbacApi, async (route) => {
      const url = route.request().url()
      const method = route.request().method()
      if (method === 'GET' && url.includes('/overrides/status')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            enabled: true,
            reloadEnabled: true,
            lastKnownGoodEnabled: true,
            failClosed: false,
            fileConfigured: true,
            loaded: true,
            fileBasename: 'admin-rbac-overrides.yaml',
            loadedAt: '2026-05-01T12:00:00Z',
            checksum: 'sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789',
            manifestVersion: '1',
            lastReloadAttemptAt: '2026-05-01T11:00:00Z',
            lastReloadResult: 'SUCCESS',
            assignmentCount: 1,
            validAssignmentCount: 1,
            ignoredAssignmentCount: 0,
            warningCount: 0,
            errorCount: 0,
            warnings: [],
          }),
        })
        return
      }
      if (method === 'POST' && url.includes('/overrides/reload')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            reloaded: true,
            result: 'SUCCESS',
            checksum: 'sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789',
            validAssignmentCount: 1,
            ignoredAssignmentCount: 0,
            warnings: [],
          }),
        })
        return
      }
      if (method === 'GET' && url.includes('/users')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ items: [], page: 0, size: 25, totalElements: 0 }),
        })
        return
      }
      await route.continue()
    })

    await signUpAndLogin(page, `rbac-reload-${Date.now()}@example.com`, 'Password1234!', 'RBAC reload E2E')
    await page.goto('/app/admin/rbac')
    await expect(page.getByRole('button', { name: 'Reload manifest' })).toBeVisible()
    await page.getByRole('button', { name: 'Reload manifest' }).click()
    await page.getByTestId('rbac-override-reload-reason').fill('E2E operator reload after GitOps configmap rollout.')
    const submit = page.getByTestId('rbac-override-reload-submit')
    await submit.scrollIntoViewIfNeeded()
    await submit.click({ force: true })
    await expect(page.getByText(/Reload succeeded/)).toBeVisible()
  })
})

import { test, expect } from '@playwright/test'
import { signUpAndLogin } from './helpers/auth.helper'
import { stubAdminRbacSession } from './helpers/stub-admin-rbac-session'
import { matchGatewayAdminRbacApi } from './helpers/gateway-admin-rbac-urls'

test.beforeEach(async ({ page }) => {
  await stubAdminRbacSession(page, 'rbac-reader')
})

test.describe('Admin RBAC overrides status (Faz 88)', () => {
  test('RBAC page shows GitOps overrides card when stubbed', async ({ page }) => {
    await page.route(matchGatewayAdminRbacApi, async (route) => {
      const url = route.request().url()
      const method = route.request().method()
      if (method === 'GET' && url.includes('/overrides/status')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            enabled: false,
            reloadEnabled: false,
            lastKnownGoodEnabled: true,
            failClosed: false,
            fileConfigured: false,
            loaded: false,
            fileBasename: '',
            loadedAt: null,
            checksum: '',
            manifestVersion: '',
            lastReloadAttemptAt: null,
            lastReloadResult: 'NEVER',
            assignmentCount: 0,
            validAssignmentCount: 0,
            ignoredAssignmentCount: 0,
            warningCount: 0,
            errorCount: 0,
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

    await signUpAndLogin(page, `rbac-ov-${Date.now()}@example.com`, 'Password1234!', 'RBAC overrides E2E')
    await page.goto('/app/admin/rbac')
    await expect(page.getByText('GitOps RBAC overrides (read-only)')).toBeVisible()
    await expect(page.getByText('Ingestion enabled')).toBeVisible()
  })
})

import { expect, test } from '@playwright/test'
import { signUpAndLogin } from './helpers/auth.helper'
import { stubMinimalAuthenticatedSession } from './helpers/stub-minimal-session'

test.beforeEach(async ({ page }) => {
  await stubMinimalAuthenticatedSession(page)
  await page.unroute('**/runtime-config.js')
  await page.route('**/runtime-config.js', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/javascript; charset=utf-8',
      body: `window.__NOTEBOOK_CONFIG__ = {
  API_BASE_URL: "",
  AUTH_TRANSPORT: "bearer",
  ADMIN_UI_ENABLED: true,
  ADMIN_UI_DEV_OPEN: false,
  AUDIT_API_MODE: "real",
};
`,
    })
  })
  await page.route('**/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        userId: 'e2e-admin-audit-user',
        email: 'admin-audit-e2e@example.com',
        name: 'Admin Audit E2E',
        roles: ['PLATFORM_ADMIN'],
        avatarUrl: null,
      }),
    })
  })
  await page.route('**/admin/audit-events**', async (route) => {
    const url = new URL(route.request().url())
    const source = url.searchParams.get('source')
    if (source === 'workspace') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [],
          page: 0,
          size: 50,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
          hasPrevious: false,
        }),
      })
      return
    }
    await route.continue()
  })
})

test('real mode shows permission denied for 403', async ({ page }) => {
  await page.route('**/admin/audit-events**', async (route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        status: 403,
        errorCode: 'ADMIN_ACCESS_DENIED',
        message: 'Admin access denied',
        path: '/admin/audit-events',
      }),
    })
  })
  await signUpAndLogin(page, `admin-audit-real-${Date.now()}@example.com`, 'Password1234!')
  await page.goto('/app/admin/audit?source=identity')
  await expect(page.getByText('Audit access denied')).toBeVisible()
})

test('real mode shows source unavailable on 503', async ({ page }) => {
  await page.route('**/admin/audit-events**', async (route) => {
    await route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        status: 503,
        errorCode: 'AUDIT_SOURCE_UNAVAILABLE',
        message: 'Audit source is unavailable',
        path: '/admin/audit-events',
      }),
    })
  })
  await signUpAndLogin(page, `admin-audit-real-${Date.now()}@example.com`, 'Password1234!')
  await page.goto('/app/admin/audit?source=identity')
  await expect(page.getByText('Selected audit source is unavailable.')).toBeVisible()
})

test('real mode renders successful page response', async ({ page }) => {
  await page.route('**/admin/audit-events**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: 'c333f4f5-4fa2-47db-95b7-e9caf9b50634',
            source: 'identity',
            eventType: 'LOGIN_SUCCESS',
            actorUserId: '2f99e3c9-f998-4f75-a3ea-2752484cb9be',
            workspaceId: null,
            aggregateType: 'USER',
            aggregateId: '2f99e3c9-f998-4f75-a3ea-2752484cb9be',
            requestId: 'req-real-mode',
            ipAddress: '127.0.0.1',
            userAgent: 'playwright',
            metadata: {},
            createdAt: '2026-05-08T08:00:00Z',
          },
        ],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      }),
    })
  })
  await signUpAndLogin(page, `admin-audit-real-${Date.now()}@example.com`, 'Password1234!')
  await page.goto('/app/admin/audit?source=identity')
  await expect(page.getByRole('cell', { name: 'LOGIN_SUCCESS' })).toBeVisible()
})

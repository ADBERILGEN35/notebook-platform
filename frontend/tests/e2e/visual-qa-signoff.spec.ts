/**
 * Faz 149 — structured visual QA execution (production preview when E2E_USE_PREVIEW=1).
 * Exercises checklist routes/viewports with stubbed APIs; asserts no secrets in DOM.
 */
import { expect, test } from '@playwright/test'
import { signUpAndLogin } from '../../e2e/helpers/auth.helper'
import { stubMinimalAuthenticatedSession } from '../../e2e/helpers/stub-minimal-session'
import { stubNonAdminSession } from './helpers/stub-non-admin-session'
import { stubSmokeAdminSession } from './helpers/stub-smoke-admin'
import { assertNoSecretsVisible } from './helpers/no-secrets'

const VIEWPORTS = {
  mobile: { width: 390, height: 844 },
  tablet: { width: 834, height: 1112 },
  desktop: { width: 1440, height: 900 },
} as const

async function visitClean(page: import('@playwright/test').Page, path: string) {
  await page.goto(path)
  await assertNoSecretsVisible(page)
}

test.describe('visual QA sign-off — auth', () => {
  test('auth pages render without secrets', async ({ page }) => {
    for (const path of ['/login', '/signup', '/forgot-password', '/mfa', '/sso/callback']) {
      await visitClean(page, path)
    }
    await page.goto('/login')
    await expect(page.getByLabel(/Email/i)).toBeVisible()
    await expect(page.getByRole('link', { name: /Skip to main content/i })).toBeAttached()
  })
})

test.describe('visual QA sign-off — AdminGate', () => {
  test('non-admin user sees permission denied on admin route', async ({ page }) => {
    await stubNonAdminSession(page)
    await signUpAndLogin(page, `nonadmin-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app/admin/overview')
    await expect(page.getByText(/Admin area restricted/i)).toBeVisible()
    await assertNoSecretsVisible(page)
  })
})

test.describe('visual QA sign-off — app shell & user domain', () => {
  test.beforeEach(async ({ page }) => {
    await stubMinimalAuthenticatedSession(page)
  })

  test('workspace hub and skip link — desktop', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.desktop)
    await signUpAndLogin(page, `vq-hub-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app')
    await expect(page.getByRole('main')).toBeVisible()
    await expect(page.getByRole('link', { name: /Skip to main content/i })).toBeAttached()
    await assertNoSecretsVisible(page)
  })

  test('mobile drawer and touch nav', async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile)
    await signUpAndLogin(page, `vq-m-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app')
    await page.getByRole('button', { name: 'Open menu' }).click()
    await expect(page.getByTestId('mobile-sidebar')).toBeVisible()
    await page.getByRole('button', { name: 'Close drawer' }).click()
    await expect(page.getByTestId('mobile-sidebar')).toBeHidden()
    await assertNoSecretsVisible(page)
  })

  test('note editor shell', async ({ page }) => {
    await page.route('**/workspaces/ws-e2e-admin-audit/notes/**', async (route) => {
      if (route.request().method() !== 'GET') {
        await route.continue()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'note-vq-1',
          title: 'Visual QA Note',
          content: '',
          workspaceId: 'ws-e2e-admin-audit',
          notebookId: 'nb-1',
        }),
      })
    })
    await signUpAndLogin(page, `vq-note-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app/workspaces/ws-e2e-admin-audit/notes/note-vq-1')
    await expect(page).toHaveURL(/note-vq-1/)
    await assertNoSecretsVisible(page)
  })

  test('search overlay Escape and settings/sync', async ({ page }) => {
    await signUpAndLogin(page, `vq-search-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app')
    await page.locator('body').click()
    await page.keyboard.press('Control+k')
    await expect(page.getByTestId('global-search-overlay')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('global-search-overlay')).toBeHidden()
    await page.goto('/app/settings')
    await expect(page.getByRole('heading', { name: /Settings|Account/i })).toBeVisible()
    await page.goto('/app/settings/sync')
    await assertNoSecretsVisible(page)
  })
})

test.describe('visual QA sign-off — admin domain', () => {
  test.beforeEach(async ({ page }) => {
    await stubSmokeAdminSession(page)
  })

  const adminRoutes = [
    '/app/admin/overview',
    '/app/admin/setup',
    '/app/admin/audit',
    '/app/admin/identity',
    '/app/admin/identity/sso',
    '/app/admin/identity/scim',
    '/app/admin/rbac',
    '/app/admin/security/break-glass',
    '/app/admin/change-requests',
    '/app/admin/notifications/analytics',
    '/app/admin/notifications/dead-letter',
    '/app/admin/notifications/legal-holds',
    '/app/admin/notifications/retention',
    '/app/admin/retention',
    '/app/admin/retention/platform',
    '/app/admin/retention/legal-holds',
  ]

  for (const [label, vp] of Object.entries(VIEWPORTS)) {
    test(`admin routes — ${label}`, async ({ page }) => {
      await page.setViewportSize(vp)
      await signUpAndLogin(page, `vq-admin-${label}-${Date.now()}@example.com`, 'Password1234!')
      for (const path of adminRoutes) {
        await page.goto(path)
        await assertNoSecretsVisible(page)
      }
    })
  }

  test('gitops diff unified viewer', async ({ page }) => {
    const id = 'cr-smoke-1'
    await page.addInitScript((crId) => {
      sessionStorage.setItem(
        `admin-cr-dry-run-${crId}`,
        JSON.stringify({
          diffPreview: '--- a/file.yaml\n+++ b/file.yaml\n@@ -1 +1 @@\n-old\n+new\n',
          targetEnvironment: 'staging',
          changedFiles: [{ path: 'file.yaml' }],
          warnings: [],
          valid: true,
        }),
      )
    }, id)
    await signUpAndLogin(page, `vq-diff-${Date.now()}@example.com`, 'Password1234!')
    await page.goto(`/app/admin/change-requests/${id}/diff`)
    await expect(page.getByTestId('gitops-diff-page')).toBeVisible()
    await expect(page.getByTestId('gitops-diff-viewer').first()).toBeVisible()
    await assertNoSecretsVisible(page)
  })
})

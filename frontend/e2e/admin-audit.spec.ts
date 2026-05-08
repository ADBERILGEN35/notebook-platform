import { test, expect } from '@playwright/test'
import { signUpAndLogin } from './helpers/auth.helper'
import { stubMinimalAuthenticatedSession } from './helpers/stub-minimal-session'

test.beforeEach(async ({ page }) => {
  await stubMinimalAuthenticatedSession(page)
  await page.addInitScript(() => {
    window.__NOTEBOOK_CONFIG__ = {
      ...(window.__NOTEBOOK_CONFIG__ ?? {}),
      ADMIN_UI_ENABLED: 'true',
      ADMIN_UI_DEV_OPEN: 'true',
      AUDIT_API_MODE: 'mock',
    }
  })
})

test.describe('admin audit UI', () => {
  test('opens audit page with filters, drawer metadata masking, and pagination', async ({ page }) => {
    const email = `admin-audit-${Date.now()}@example.com`
    const password = 'Password1234!'
    await signUpAndLogin(page, email, password)

    await page.goto('/app/admin/audit?source=identity&size=25&page=0&sort=createdAt,desc')
    await expect(page.getByRole('heading', { name: 'Audit Events' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Export' })).toBeVisible()
    await page.getByRole('button', { name: 'Export' }).click()
    await expect(page.getByText('Export requires createdFrom and createdTo filters.')).toBeVisible()

    await page.getByRole('button', { name: 'workspace', exact: true }).click()
    await expect(page.getByRole('cell', { name: 'WORKSPACE_CREATED' })).toBeVisible()

    await page.getByRole('button', { name: 'identity', exact: true }).click()

    await page.getByPlaceholder('eventType (exact)').fill('REFRESH_TOKEN_ROTATED')
    await page.getByRole('button', { name: 'Apply filters' }).click()
    await expect(page.getByRole('cell', { name: 'REFRESH_TOKEN_ROTATED' })).toBeVisible()

    await page.locator('tbody tr').filter({ hasText: 'REFRESH_TOKEN_ROTATED' }).first().click()
    await expect(page.getByText('Metadata (masked for display)', { exact: false })).toBeVisible()

    await expect(page.getByText('"bearerToken": "***masked***"')).toBeVisible()
    await expect(page.getByText('should-mask-in-ui')).not.toBeVisible()

    await page.goto('/app/admin/audit?source=identity&size=25&page=0&sort=createdAt,desc')
    await expect(page.getByRole('button', { name: 'Next' })).toBeEnabled()
    await expect(page.getByRole('button', { name: 'Previous' })).toBeDisabled()
    await page.getByRole('button', { name: 'Next' }).click()
    await expect(page.getByRole('button', { name: 'Previous' })).toBeEnabled()
  })
})

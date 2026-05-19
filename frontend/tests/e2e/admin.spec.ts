import { expect, test } from '@playwright/test'
import { signUpAndLogin } from '../../e2e/helpers/auth.helper'
import { stubSmokeAdminSession } from './helpers/stub-smoke-admin'
import { assertNoSecretsVisible } from './helpers/no-secrets'

test.describe('admin overview smoke', () => {
  test.beforeEach(async ({ page }) => {
    await stubSmokeAdminSession(page)
  })

  test('admin overview renders for platform admin', async ({ page }) => {
    await signUpAndLogin(page, `admin-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app/admin/overview')
    await expect(page.getByRole('heading', { name: /Admin overview/i })).toBeVisible()
    await assertNoSecretsVisible(page)
  })

  test('admin layout nav includes retention hub on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await signUpAndLogin(page, `admin-m-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app/admin/retention')
    await expect(page.getByTestId('admin-retention-hub')).toBeVisible()
    await assertNoSecretsVisible(page)
  })
})

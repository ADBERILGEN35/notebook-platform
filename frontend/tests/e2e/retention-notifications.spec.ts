import { expect, test } from '@playwright/test'
import { signUpAndLogin } from '../../e2e/helpers/auth.helper'
import { stubSmokeAdminSession } from './helpers/stub-smoke-admin'
import { assertNoSecretsVisible } from './helpers/no-secrets'

test.describe('retention and notification ops smoke', () => {
  test.beforeEach(async ({ page }) => {
    await stubSmokeAdminSession(page)
  })

  test('notification analytics page loads', async ({ page }) => {
    await signUpAndLogin(page, `na-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app/admin/notifications/analytics')
    await expect(page.getByRole('heading', { name: /Notification analytics/i })).toBeVisible()
    await assertNoSecretsVisible(page)
  })

  test('dead-letter queue page loads', async ({ page }) => {
    await signUpAndLogin(page, `dl-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app/admin/notifications/dead-letter')
    await expect(page.getByRole('heading', { name: /Notification dead-letter/i })).toBeVisible()
    await assertNoSecretsVisible(page)
  })

  test('notification retention overview loads', async ({ page }) => {
    await signUpAndLogin(page, `ret-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app/admin/notifications/retention')
    await expect(page.getByRole('heading', { name: /Notification retention/i })).toBeVisible()
    await assertNoSecretsVisible(page)
  })

  test('platform retention governance loads on tablet width', async ({ page }) => {
    await page.setViewportSize({ width: 820, height: 1180 })
    await signUpAndLogin(page, `pr-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app/admin/retention/platform')
    await expect(page.getByRole('heading', { name: 'Platform retention' })).toBeVisible()
    await assertNoSecretsVisible(page)
  })
})

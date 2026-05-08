import { expect, test } from '@playwright/test'
import { signUpAndLogin } from './helpers/auth.helper'
import { stubMinimalAuthenticatedSession } from './helpers/stub-minimal-session'

test.describe('responsive shell smoke', () => {
  test.beforeEach(async ({ page }) => {
    await stubMinimalAuthenticatedSession(page)
    await page.addInitScript(() => {
      window.__NOTEBOOK_CONFIG__ = {
        ...(window.__NOTEBOOK_CONFIG__ ?? {}),
        NOTIFICATIONS_ENABLED: 'true',
      }
    })
  })

  test('mobile sidebar opens and closes', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await signUpAndLogin(page, `responsive-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app')
    await page.getByTestId('sidebar-toggle').click()
    await expect(page.getByTestId('mobile-sidebar')).toBeVisible()
    await page.getByRole('button', { name: 'Close drawer' }).click()
    await expect(page.getByTestId('mobile-sidebar')).toBeHidden()
  })

  test('tablet shell keeps top controls accessible', async ({ page }) => {
    await page.setViewportSize({ width: 820, height: 1180 })
    await signUpAndLogin(page, `responsive-tab-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app')
    await expect(page.getByTestId('sidebar-toggle')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Security' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Create note' })).toBeVisible()
  })
})

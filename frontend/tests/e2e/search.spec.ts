import { expect, test } from '@playwright/test'
import { signUpAndLogin } from '../../e2e/helpers/auth.helper'
import { stubMinimalAuthenticatedSession } from '../../e2e/helpers/stub-minimal-session'
import { assertNoSecretsVisible } from './helpers/no-secrets'

test.describe('search smoke', () => {
  test.beforeEach(async ({ page }) => {
    await stubMinimalAuthenticatedSession(page)
  })

  test('Ctrl+K opens global search overlay', async ({ page }) => {
    await signUpAndLogin(page, `search-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app')
    await page.locator('body').click()
    await page.keyboard.press('Control+k')
    await expect(page.getByTestId('global-search-overlay')).toBeVisible()
    await expect(page.getByRole('dialog', { name: 'Global search' })).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('global-search-overlay')).toBeHidden()
    await assertNoSecretsVisible(page)
  })

  test('settings route loads', async ({ page }) => {
    await signUpAndLogin(page, `settings-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app/settings')
    await expect(page.getByRole('heading', { name: /Settings|Account/i })).toBeVisible()
    await assertNoSecretsVisible(page)
  })
})

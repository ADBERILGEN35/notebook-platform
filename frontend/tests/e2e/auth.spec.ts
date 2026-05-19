import { expect, test } from '@playwright/test'
import { assertNoSecretsVisible } from './helpers/no-secrets'

test.describe('auth smoke', () => {
  test('login page renders accessible form', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByRole('button', { name: /Continue|Sign in/i })).toBeVisible()
    await expect(page.getByLabel(/Email/i)).toBeVisible()
    await expect(page.getByLabel(/Password/i)).toBeVisible()
    await expect(page.getByRole('link', { name: /Skip to main content/i })).toBeAttached()
    await assertNoSecretsVisible(page)
  })

  test('signup page renders', async ({ page }) => {
    await page.goto('/signup')
    await expect(page.getByRole('heading', { name: /Create your workspace/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /Create account/i })).toBeVisible()
    await assertNoSecretsVisible(page)
  })
})

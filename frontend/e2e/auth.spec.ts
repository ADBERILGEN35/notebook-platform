import { expect, test } from '@playwright/test'
import { createE2eData } from './helpers/test-data'
import { login, logoutFromSettings, signUpAndLogin } from './helpers/auth.helper'

test.describe('auth journey', () => {
  test('signup successful and redirects to app', async ({ page }) => {
    const data = createE2eData()
    await signUpAndLogin(page, data.email, data.password)
    await expect(page).toHaveURL(/\/app/)
    const accessToken = await page.evaluate(() => localStorage.getItem('np_access_token'))
    expect(accessToken).toBeTruthy()
  })

  test('invalid login shows error', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('Email').fill('invalid@example.com')
    await page.getByPlaceholder('Password').fill('wrong-password')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByText(/validation|invalid|error|credential|forbidden/i)).toBeVisible({
      timeout: 10_000,
    })
  })

  test('logout clears session and returns login', async ({ page }) => {
    const data = createE2eData()
    await signUpAndLogin(page, data.email, data.password)
    await logoutFromSettings(page)
    await expect(page).toHaveURL(/\/login/)
  })

  test('revoke-all action available in settings', async ({ page }) => {
    const data = createE2eData()
    await signUpAndLogin(page, data.email, data.password)
    await page.goto('/app/settings/security')
    await expect(page.getByRole('button', { name: 'Revoke all sessions' })).toBeVisible()
  })

  test('refresh preserves session after reload', async ({ page }) => {
    const data = createE2eData()
    await signUpAndLogin(page, data.email, data.password)
    await page.reload()
    await expect(page).toHaveURL(/\/app/)
  })

  test('login successful for existing user', async ({ page }) => {
    const data = createE2eData()
    await signUpAndLogin(page, data.email, data.password)
    await logoutFromSettings(page)
    await login(page, data.email, data.password)
    await expect(page).toHaveURL(/\/app/)
  })
})


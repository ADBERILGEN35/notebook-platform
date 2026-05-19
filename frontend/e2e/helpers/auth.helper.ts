import type { Page } from '@playwright/test'

export async function signUpAndLogin(page: Page, email: string, password: string, name = 'E2E User') {
  await page.goto('/signup')
  await page.getByLabel(/Full name/i).fill(name)
  await page.getByLabel(/Work email|Email/i).fill(email)
  await page.getByLabel(/^Password$/i).fill(password)
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: /Create account|Sign up/i }).click()
  await page.waitForURL('**/app**')
}

export async function login(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.getByLabel(/^Email$/i).fill(email)
  await page.getByLabel(/^Password$/i).fill(password)
  await page.getByRole('button', { name: /Continue|Sign in/i }).click()
  await page.waitForURL('**/app**')
}

export async function logoutFromSettings(page: Page) {
  await page.goto('/app/settings/security')
  await page.getByRole('button', { name: 'Logout' }).click()
  await page.waitForURL('**/login')
}

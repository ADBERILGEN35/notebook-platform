import type { Page } from '@playwright/test'

export async function signUpAndLogin(page: Page, email: string, password: string, name = 'E2E User') {
  await page.goto('/signup')
  await page.getByPlaceholder('Name').fill(name)
  await page.getByPlaceholder('Email').fill(email)
  await page.getByPlaceholder('Password (min 10 chars)').fill(password)
  await page.getByRole('button', { name: 'Sign up' }).click()
  await page.waitForURL('**/app**')
}

export async function login(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.getByPlaceholder('Email').fill(email)
  await page.getByPlaceholder('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.waitForURL('**/app**')
}

export async function logoutFromSettings(page: Page) {
  await page.goto('/app/settings/security')
  await page.getByRole('button', { name: 'Logout' }).click()
  await page.waitForURL('**/login')
}


import { expect, test } from '@playwright/test'
import { createE2eData } from './helpers/test-data'
import { signUpAndLogin } from './helpers/auth.helper'

test('settings security smoke + error state interception', async ({ page }) => {
  const data = createE2eData()
  await signUpAndLogin(page, data.email, data.password)
  await page.goto('/app/settings/security')
  await expect(page.getByText(/localStorage/i)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Revoke all sessions' })).toBeVisible()

  await page.route('**/auth/revoke-all', async (route) => {
    await route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        status: 503,
        errorCode: 'SERVICE_UNAVAILABLE',
        message: 'Service unavailable',
        path: '/auth/revoke-all',
        requestId: 'e2e-503',
      }),
    })
  })

  await page.getByRole('button', { name: 'Revoke all sessions' }).click()
  await expect(page.getByText(/service unavailable/i)).toBeVisible()
})

test('error state mappings for 429, 403 and 404 via interception', async ({ page }) => {
  const data = createE2eData()
  await signUpAndLogin(page, data.email, data.password)

  await page.route('**/search/notes**', async (route) => {
    await route.fulfill({
      status: 429,
      contentType: 'application/json',
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        status: 429,
        errorCode: 'RATE_LIMITED',
        message: 'Too many requests',
        path: '/search/notes',
        requestId: 'e2e-429',
      }),
    })
  })
  await page.goto('/app/search')
  await page.getByPlaceholder('Type at least 2 chars...').fill('rate-limit')
  await expect(page.getByText(/rate limit/i)).toBeVisible()

  await page.unroute('**/search/notes**')
  await page.route('**/search/notes**', async (route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        status: 403,
        errorCode: 'FORBIDDEN',
        message: 'Forbidden',
        path: '/search/notes',
        requestId: 'e2e-403',
      }),
    })
  })
  await page.goto('/app/search')
  await page.getByPlaceholder('Type at least 2 chars...').fill('forbidden')
  await expect(page.getByText(/permission denied/i)).toBeVisible()

  await page.unroute('**/search/notes**')
  await page.route('**/notes/*', async (route) => {
    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        status: 404,
        errorCode: 'NOT_FOUND',
        message: 'Not found',
        path: '/notes/abc',
        requestId: 'e2e-404',
      }),
    })
  })
  await page.goto('/app/notes/00000000-0000-0000-0000-000000000000')
  await expect(page.getByText(/not found/i)).toBeVisible()
})

test('401 response clears session and redirects login', async ({ page }) => {
  const data = createE2eData()
  await signUpAndLogin(page, data.email, data.password)

  await page.route('**/workspaces**', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        status: 401,
        errorCode: 'UNAUTHORIZED',
        message: 'Unauthorized',
        path: '/workspaces',
        requestId: 'e2e-401',
      }),
    })
  })

  await page.goto('/app/workspaces/00000000-0000-0000-0000-000000000000')
  await expect(page).toHaveURL(/\/login/)
})


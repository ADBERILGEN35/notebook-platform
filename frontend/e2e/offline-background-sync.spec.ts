import { expect, test } from '@playwright/test'
import { createE2eData } from './helpers/test-data'
import { signUpAndLogin } from './helpers/auth.helper'

test('prompt mode shows banner and sync summary', async ({ page }) => {
  const data = createE2eData()
  await page.addInitScript(() => {
    ;(window as unknown as { __NOTEBOOK_CONFIG__?: Record<string, unknown> }).__NOTEBOOK_CONFIG__ = {
      ...(window as unknown as { __NOTEBOOK_CONFIG__?: Record<string, unknown> }).__NOTEBOOK_CONFIG__,
      OFFLINE_EDIT_ENABLED: 'true',
      OFFLINE_SYNC_ENABLED: 'true',
      OFFLINE_SYNC_ROLLOUT_MODE: 'manual',
      OFFLINE_BACKGROUND_SYNC_ENABLED: 'true',
      OFFLINE_BACKGROUND_SYNC_MODE: 'prompt',
    }
  })
  await signUpAndLogin(page, data.email, data.password)
  await page.goto('/app/settings/security')
  await expect(page.getByText(/offline drafts/i)).toBeVisible()
  await page.getByRole('button', { name: 'Run foreground background sync' }).click()
  await expect(page.getByText(/last background sync/i)).toBeVisible()
})

test('conflict sync response keeps review path visible', async ({ page }) => {
  const data = createE2eData()
  await page.addInitScript(() => {
    ;(window as unknown as { __NOTEBOOK_CONFIG__?: Record<string, unknown> }).__NOTEBOOK_CONFIG__ = {
      ...(window as unknown as { __NOTEBOOK_CONFIG__?: Record<string, unknown> }).__NOTEBOOK_CONFIG__,
      OFFLINE_EDIT_ENABLED: 'true',
      OFFLINE_SYNC_ENABLED: 'true',
      OFFLINE_SYNC_ROLLOUT_MODE: 'manual',
      OFFLINE_BACKGROUND_SYNC_ENABLED: 'true',
      OFFLINE_BACKGROUND_SYNC_MODE: 'prompt',
    }
  })
  await signUpAndLogin(page, data.email, data.password)
  await page.route('**/notes/*', async (route) => {
    if (route.request().method() === 'PATCH') {
      await route.fulfill({
        status: 412,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 412,
          errorCode: 'NOTE_CONFLICT',
          message: 'Conflict',
          path: route.request().url(),
          requestId: 'e2e-conflict',
        }),
      })
      return
    }
    await route.continue()
  })
  await page.goto('/app/settings/security')
  await page.getByRole('button', { name: 'Run foreground background sync' }).click()
  await expect(page.getByText(/needs review|conflict/i)).toBeVisible()
})

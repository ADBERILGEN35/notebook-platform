import { expect, test } from '@playwright/test'
import { createE2eData } from './helpers/test-data'
import { signUpAndLogin } from './helpers/auth.helper'

test('settings notifications page opens and mandatory security toggle is disabled', async ({ page }) => {
  const data = createE2eData()
  await signUpAndLogin(page, data.email, data.password)
  await page.goto('/app/settings/notifications')
  await expect(page.getByText('Notification preferences')).toBeVisible()
  await expect(page.getByText(/Required for account security/i)).toBeVisible()
  await expect(page.getByLabel('SECURITY_SESSIONS_REVOKED-EMAIL')).toBeDisabled()
})

test('save notification preferences smoke', async ({ page }) => {
  const data = createE2eData()
  await signUpAndLogin(page, data.email, data.password)
  await page.goto('/app/settings/notifications')
  const commentEmail = page.getByLabel('COMMENT_ADDED-EMAIL')
  await commentEmail.click()
  await page.getByRole('button', { name: 'Save changes' }).click()
})

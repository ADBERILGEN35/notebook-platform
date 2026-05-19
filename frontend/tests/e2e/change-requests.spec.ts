import { expect, test } from '@playwright/test'
import { signUpAndLogin } from '../../e2e/helpers/auth.helper'
import { stubSmokeAdminSession } from './helpers/stub-smoke-admin'
import { assertNoSecretsVisible } from './helpers/no-secrets'

test.describe('change requests smoke', () => {
  test.beforeEach(async ({ page }) => {
    await stubSmokeAdminSession(page)
  })

  test('change requests list renders', async ({ page }) => {
    await signUpAndLogin(page, `cr-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app/admin/change-requests')
    await expect(page.getByTestId('change-request-list-table')).toBeVisible()
    await assertNoSecretsVisible(page)
  })

  test('gitops diff page shows unified viewer after dry-run storage seed', async ({ page }) => {
    await signUpAndLogin(page, `cr-diff-${Date.now()}@example.com`, 'Password1234!')
    const id = 'cr-smoke-1'
    await page.addInitScript((crId) => {
      sessionStorage.setItem(
        `admin-cr-dry-run-${crId}`,
        JSON.stringify({
          diffPreview: '--- a/file.yaml\n+++ b/file.yaml\n@@ -1 +1 @@\n-old\n+new\n',
          targetEnvironment: 'staging',
          changedFiles: [{ path: 'file.yaml' }],
          warnings: [],
          valid: true,
        }),
      )
    }, id)
    await page.goto(`/app/admin/change-requests/${id}/diff`)
    await expect(page.getByTestId('gitops-diff-page')).toBeVisible()
    await expect(page.getByTestId('gitops-diff-viewer').first()).toBeVisible()
    await assertNoSecretsVisible(page)
  })
})

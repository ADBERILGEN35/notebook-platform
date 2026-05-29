import { expect, test } from '@playwright/test'
import { signUpAndLogin } from '../../e2e/helpers/auth.helper'
import { stubMinimalAuthenticatedSession } from '../../e2e/helpers/stub-minimal-session'
import { assertNoSecretsVisible } from './helpers/no-secrets'

test.describe('app shell smoke', () => {
  test.beforeEach(async ({ page }) => {
    await stubMinimalAuthenticatedSession(page)
  })

  test('workspace hub loads on desktop', async ({ page }) => {
    await signUpAndLogin(page, `shell-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app')
    await expect(page.getByRole('main')).toBeVisible()
    await expect(page.getByRole('button', { name: /Create note|Create/ })).toBeVisible()
    await assertNoSecretsVisible(page)
  })

  test('note editor route renders shell', async ({ page }) => {
    await page.route('**/workspaces/ws-e2e-admin-audit/notes/**', async (route) => {
      if (route.request().method() !== 'GET') {
        await route.continue()
        return
      }
      const url = route.request().url()
      if (url.includes('/history')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ items: [] }),
        })
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'note-e2e-1',
          title: 'E2E Note',
          content: '',
          workspaceId: 'ws-e2e-admin-audit',
          notebookId: 'nb-1',
        }),
      })
    })
    await signUpAndLogin(page, `note-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app/workspaces/ws-e2e-admin-audit/notes/note-e2e-1')
    await expect(page).toHaveURL(/\/notes\/note-e2e-1/)
    await assertNoSecretsVisible(page)
  })

  test('mobile drawer opens and closes', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await signUpAndLogin(page, `shell-m-${Date.now()}@example.com`, 'Password1234!')
    await page.goto('/app')
    await page.getByRole('button', { name: 'Open menu' }).click()
    await expect(page.getByTestId('mobile-sidebar')).toBeVisible()
    await page.getByRole('button', { name: 'Close drawer' }).click()
    await expect(page.getByTestId('mobile-sidebar')).toBeHidden()
  })
})

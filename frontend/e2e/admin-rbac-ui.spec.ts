import { test, expect } from '@playwright/test'
import { signUpAndLogin } from './helpers/auth.helper'
import { stubAdminRbacSession } from './helpers/stub-admin-rbac-session'

test.describe('admin RBAC UI (Faz 79)', () => {
  test('audit viewer sees audit nav but not export', async ({ page }) => {
    await stubAdminRbacSession(page, 'audit-viewer')
    const email = `rbac-audit-${Date.now()}@example.com`
    await signUpAndLogin(page, email, 'Password1234!')

    await page.goto('/app/admin')
    await expect(page.getByRole('link', { name: 'Audit events', exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Enterprise console', exact: true })).not.toBeVisible()

    await page.goto('/app/admin/audit?source=identity&size=25&page=0&sort=createdAt,desc')
    await expect(page.getByRole('heading', { name: 'Audit Events' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Export' })).not.toBeVisible()
  })

  test('change approver sees approve but not create flow', async ({ page }) => {
    await stubAdminRbacSession(page, 'change-approver')
    const email = `rbac-appr-${Date.now()}@example.com`
    await signUpAndLogin(page, email, 'Password1234!')

    await page.goto('/app/admin/enterprise/change-requests')
    await expect(page.getByText('Missing admin:change-request:create permission.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Validate' })).toBeDisabled()
    await page.locator('tbody').getByRole('link', { name: 'View', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Approve', exact: true })).toBeVisible()
  })

  test('user without admin access gets gate message', async ({ page }) => {
    await stubAdminRbacSession(page, 'no-admin-access')
    const email = `rbac-none-${Date.now()}@example.com`
    await signUpAndLogin(page, email, 'Password1234!')

    await page.goto('/app/admin')
    await expect(page.getByText('Admin area restricted')).toBeVisible()
  })
})

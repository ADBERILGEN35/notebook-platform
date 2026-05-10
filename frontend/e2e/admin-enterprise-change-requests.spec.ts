import { test, expect } from '@playwright/test'
import { signUpAndLogin } from './helpers/auth.helper'
import { stubMinimalAuthenticatedSession } from './helpers/stub-minimal-session'

const STUB_USER_ID = 'e2e-admin-audit-user'

test.beforeEach(async ({ page }) => {
  await stubMinimalAuthenticatedSession(page)
  await page.unroute('**/runtime-config.js')
  await page.route('**/runtime-config.js', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/javascript; charset=utf-8',
      body: `window.__NOTEBOOK_CONFIG__ = {
  API_BASE_URL: "",
  AUTH_TRANSPORT: "bearer",
  ADMIN_UI_ENABLED: true,
  ADMIN_UI_DEV_OPEN: true,
  ENTERPRISE_ADMIN_WRITE_ENABLED: true,
  ENTERPRISE_ADMIN_APPROVALS_ENABLED: true,
  AUDIT_API_MODE: "mock",
};
`,
    })
  })
})

test.describe('Enterprise change requests', () => {
  test('validate and create flow with mocked gateway', async ({ page }) => {
    let pending: Record<string, unknown> | null = null

    await page.route('**/admin/enterprise/change-requests**', async (route) => {
      const url = route.request().url()
      const method = route.request().method()
      if (method === 'GET') {
        const items = pending ? [pending] : []
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ items }),
        })
        return
      }
      if (method === 'POST' && url.includes('/validate')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            valid: true,
            requiresApproval: true,
            impactSummary: {
              severity: 'MEDIUM',
              description: 'test',
              rollback: 'revert',
              affectedSurfaces: [],
              runtimeApplySupported: false,
            },
            validationResult: { valid: true },
          }),
        })
        return
      }
      if (method === 'POST' && url.includes('/cancel')) {
        await route.fulfill({ status: 204, body: '' })
        return
      }
      if (method === 'POST') {
        pending = {
          id: '11111111-1111-1111-1111-111111111111',
          requestedByUserId: STUB_USER_ID,
          status: 'PENDING',
          operationType: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
          targetService: 'content-service',
          targetKey: 'NOTE_MERGE_ANALYSIS_ENABLED',
          currentValue: null,
          requestedValue: 'true',
          severity: 'MEDIUM',
          impactSummary: {
            severity: 'MEDIUM',
            description: 'row impact',
            rollback: 'revert',
          },
          validationResult: {},
          createdAt: new Date().toISOString(),
          externalRequestId: null,
          decidedAt: null,
          decidedByUserId: null,
          decisionReason: null,
          approvedAt: null,
          rejectedAt: null,
        }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: pending.id,
            status: 'PENDING',
            operationType: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
            createdAt: new Date().toISOString(),
          }),
        })
        return
      }
      await route.continue()
    })

    await signUpAndLogin(page, `chg-req-${Date.now()}@example.com`, 'Password1234!', 'Change Req E2E')
    await page.goto('/app/admin/enterprise/change-requests')
    await expect(page.getByRole('heading', { name: /Enterprise change requests/i })).toBeVisible()
    await page.getByRole('button', { name: 'Validate' }).click()
    await expect(page.getByText(/Impact preview/i)).toBeVisible()
    await page.getByRole('button', { name: 'Create pending request' }).click()
    await expect(page.getByText('MERGE_ANALYSIS_ROLLOUT_REQUEST')).toBeVisible()
  })

  test('approve flow for another admin', async ({ page }) => {
    const row = {
      id: '22222222-2222-2222-2222-222222222222',
      requestedByUserId: '99999999-9999-9999-9999-999999999999',
      status: 'PENDING',
      operationType: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
      targetService: 'content-service',
      targetKey: 'NOTE_MERGE_ANALYSIS_ENABLED',
      currentValue: null,
      requestedValue: 'true',
      severity: 'MEDIUM',
      impactSummary: { severity: 'MEDIUM', description: 'impact', rollback: 'rb' },
      validationResult: {},
      createdAt: new Date().toISOString(),
      externalRequestId: null,
      decidedAt: null,
      decidedByUserId: null,
      decisionReason: null,
      approvedAt: null,
      rejectedAt: null,
    }

    await page.route('**/admin/enterprise/change-requests**', async (route) => {
      const url = route.request().url()
      const method = route.request().method()
      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ items: [row] }),
        })
        return
      }
      if (method === 'POST' && url.includes('/approve')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: row.id,
            status: 'APPROVED',
            decidedAt: new Date().toISOString(),
            decidedByUserId: STUB_USER_ID,
            operationType: row.operationType,
            targetService: row.targetService,
            targetKey: row.targetKey,
            requestedValue: row.requestedValue,
            nextStep: { type: 'GITOPS_OR_MANUAL_APPLY', message: 'Apply via GitOps.' },
          }),
        })
        return
      }
      await route.continue()
    })

    await signUpAndLogin(page, `chg-appr-${Date.now()}@example.com`, 'Password1234!', 'Approve E2E')
    await page.goto('/app/admin/enterprise/change-requests')
    await page.getByRole('button', { name: 'Approve' }).click()
    await expect(page.getByText(/This does not apply the change automatically/i)).toBeVisible()
    await page.getByRole('button', { name: 'Approve' }).nth(1).click()
    await expect(page.getByText(/Request approved/i)).toBeVisible()
  })

  test('self-created request has approve disabled', async ({ page }) => {
    const row = {
      id: '33333333-3333-3333-3333-333333333333',
      requestedByUserId: STUB_USER_ID,
      status: 'PENDING',
      operationType: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
      targetService: 'content-service',
      targetKey: 'NOTE_MERGE_ANALYSIS_ENABLED',
      currentValue: null,
      requestedValue: 'true',
      severity: 'MEDIUM',
      impactSummary: {},
      validationResult: {},
      createdAt: new Date().toISOString(),
      externalRequestId: null,
      decidedAt: null,
      decidedByUserId: null,
      decisionReason: null,
      approvedAt: null,
      rejectedAt: null,
    }
    await page.route('**/admin/enterprise/change-requests**', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ items: [row] }),
        })
        return
      }
      await route.continue()
    })
    await signUpAndLogin(page, `chg-self-${Date.now()}@example.com`, 'Password1234!', 'Self E2E')
    await page.goto('/app/admin/enterprise/change-requests')
    await expect(page.getByRole('button', { name: 'Approve' })).toBeDisabled()
  })

  test('status filter calls API with query', async ({ page }) => {
    const seen: string[] = []
    await page.route('**/admin/enterprise/change-requests**', async (route) => {
      if (route.request().method() === 'GET') {
        seen.push(route.request().url())
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ items: [] }),
        })
        return
      }
      await route.continue()
    })
    await signUpAndLogin(page, `chg-filt-${Date.now()}@example.com`, 'Password1234!', 'Filter E2E')
    await page.goto('/app/admin/enterprise/change-requests')
    await page.getByRole('button', { name: 'Approved' }).click()
    await expect.poll(() => seen.some((u) => u.includes('status=APPROVED'))).toBeTruthy()
  })

  test('reject flow', async ({ page }) => {
    const row = {
      id: '44444444-4444-4444-4444-444444444444',
      requestedByUserId: '88888888-8888-8888-8888-888888888888',
      status: 'PENDING',
      operationType: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
      targetService: 'content-service',
      targetKey: 'NOTE_MERGE_ANALYSIS_ENABLED',
      currentValue: null,
      requestedValue: 'true',
      severity: 'LOW',
      impactSummary: {},
      validationResult: {},
      createdAt: new Date().toISOString(),
      externalRequestId: null,
      decidedAt: null,
      decidedByUserId: null,
      decisionReason: null,
      approvedAt: null,
      rejectedAt: null,
    }
    await page.route('**/admin/enterprise/change-requests**', async (route) => {
      const url = route.request().url()
      const method = route.request().method()
      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ items: [row] }),
        })
        return
      }
      if (method === 'POST' && url.includes('/reject')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: row.id,
            status: 'REJECTED',
            decisionReason: 'no',
            decidedAt: new Date().toISOString(),
          }),
        })
        return
      }
      await route.continue()
    })
    await signUpAndLogin(page, `chg-rej-${Date.now()}@example.com`, 'Password1234!', 'Reject E2E')
    await page.goto('/app/admin/enterprise/change-requests')
    await page.getByRole('button', { name: 'Reject' }).click()
    await page.locator('textarea').fill('Not ready for rollout')
    await page.getByRole('button', { name: 'Reject' }).nth(1).click()
    await expect(page.getByText('MERGE_ANALYSIS_ROLLOUT_REQUEST')).toBeVisible()
  })

  test('403 on list shows permission message', async ({ page }) => {
    await page.route('**/admin/enterprise/change-requests**', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 403,
          contentType: 'application/json',
          body: JSON.stringify({
            timestamp: new Date().toISOString(),
            status: 403,
            errorCode: 'ADMIN_ACCESS_DENIED',
            message: 'no',
            path: '/admin/enterprise/change-requests',
          }),
        })
        return
      }
      await route.continue()
    })

    await signUpAndLogin(page, `chg-denied-${Date.now()}@example.com`, 'Password1234!', 'Denied E2E')
    await page.goto('/app/admin/enterprise/change-requests')
    await expect(page.getByText(/Permission denied/i)).toBeVisible()
  })
})

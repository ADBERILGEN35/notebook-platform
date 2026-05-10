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

test('workspace notification preferences section and mocked 403 save error', async ({ page }) => {
  const data = createE2eData()
  await page.addInitScript(() => {
    ;(window as unknown as { __NOTEBOOK_CONFIG__?: Record<string, unknown> }).__NOTEBOOK_CONFIG__ = {
      ...(window as unknown as { __NOTEBOOK_CONFIG__?: Record<string, unknown> }).__NOTEBOOK_CONFIG__,
      NOTIFICATION_PREFERENCES_ENABLED: 'true',
      WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED: 'true',
    }
  })
  await signUpAndLogin(page, data.email, data.password)

  const wsId = '00000000-0000-0000-0000-000000000099'
  await page.route(`**/workspaces?**`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [{ id: wsId, slug: 'e2e-ws', name: 'E2E Workspace', type: 'PERSONAL', ownerId: 'x', createdAt: '', updatedAt: '' }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      }),
    })
  })

  await page.route(`**/notification-preferences/workspaces/${wsId}`, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          workspaceId: wsId,
          preferences: [
            {
              notificationType: 'COMMENT_ADDED',
              label: 'Comments',
              description: 'When someone comments in this workspace.',
              channels: {
                IN_APP: {
                  enabled: true,
                  inherited: true,
                  effectiveEnabled: true,
                  mandatory: false,
                },
                EMAIL: {
                  enabled: false,
                  inherited: true,
                  effectiveEnabled: false,
                  mandatory: false,
                },
              },
            },
          ],
        }),
      })
      return
    }
    if (route.request().method() === 'PATCH') {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 403,
          errorCode: 'WORKSPACE_NOTIFICATION_PREFERENCE_ACCESS_DENIED',
          message: 'User is not a member of this workspace',
          path: route.request().url(),
          requestId: 'e2e-ws-pref',
        }),
      })
      return
    }
    await route.continue()
  })

  await page.goto('/app/settings/security')
  await expect(page.getByText('Workspace notification preferences')).toBeVisible()
  await expect(page.getByLabel('workspace-notification-workspace')).toBeVisible()

  await page.getByLabel('workspace-COMMENT_ADDED-EMAIL-inherit').uncheck()
  await page.getByRole('button', { name: 'Save workspace overrides' }).click()
  await expect(page.getByText(/permission denied for this operation/i)).toBeVisible()
})

test('workspace admin notification policies: mock owner saves force-enabled', async ({ page }) => {
  const data = createE2eData()
  await page.addInitScript(() => {
    ;(window as unknown as { __NOTEBOOK_CONFIG__?: Record<string, unknown> }).__NOTEBOOK_CONFIG__ = {
      ...(window as unknown as { __NOTEBOOK_CONFIG__?: Record<string, unknown> }).__NOTEBOOK_CONFIG__,
      NOTIFICATION_PREFERENCES_ENABLED: 'true',
      WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED: 'false',
      WORKSPACE_NOTIFICATION_POLICIES_ENABLED: 'true',
    }
  })
  await signUpAndLogin(page, data.email, data.password)

  const wsId = '00000000-0000-0000-0000-000000000088'
  await page.route(`**/workspaces?**`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: wsId,
            slug: 'e2e-pol',
            name: 'Policy Workspace',
            type: 'PERSONAL',
            ownerId: 'x',
            createdAt: '',
            updatedAt: '',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      }),
    })
  })

  const policyBody = (inAppMode: string, reason: string | null) =>
    JSON.stringify({
      workspaceId: wsId,
      canManagePolicies: true,
      policies: [
        {
          notificationType: 'COMMENT_ADDED',
          label: 'Comments',
          channels: {
            IN_APP: { policyMode: inAppMode, reason, manageable: true },
            EMAIL: { policyMode: 'USER_CONTROLLED', reason: null, manageable: true },
          },
        },
      ],
    })

  await page.route(`**/notification-policies/workspaces/${wsId}`, async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: policyBody('USER_CONTROLLED', null),
      })
      return
    }
    if (method === 'PATCH') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: policyBody('FORCE_ENABLED', 'E2E collaboration requirement'),
      })
      return
    }
    await route.continue()
  })

  await page.goto('/app/settings/security')
  await expect(page.getByText('Workspace notifications')).toBeVisible()
  await expect(page.getByText('Admin notification policies')).toBeVisible()

  await page.getByLabel('policy-COMMENT_ADDED-IN_APP-mode').selectOption('FORCE_ENABLED')
  await page.getByLabel('policy-COMMENT_ADDED-IN_APP-reason').fill('E2E collaboration requirement')
  await page.getByRole('button', { name: 'Save workspace policies' }).click()
  await expect(page.getByText('Workspace policies saved.')).toBeVisible()
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


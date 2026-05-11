import type { Page } from '@playwright/test'
import { matchGatewayChangeRequestsApi } from './gateway-stub-urls'

export type AdminRbacE2eProfile =
  | 'audit-viewer'
  | 'change-approver'
  | 'no-admin-access'
  | 'rbac-reader'
  | 'rbac-override-reloader'

/**
 * Bearer-mode stubs with ADMIN_UI_DEV_OPEN off so RBAC helpers drive visibility (Faz 79 E2E).
 */
export async function stubAdminRbacSession(page: Page, profile: AdminRbacE2eProfile) {
  const enterpriseWrite = profile === 'change-approver' ? 'true' : 'false'
  const rbacUi =
    profile === 'rbac-reader' || profile === 'rbac-override-reloader' ? 'true' : 'false'
  const rbacOverridesStatus =
    profile === 'rbac-reader' || profile === 'rbac-override-reloader' ? 'true' : 'false'
  const rbacOverridesReload = profile === 'rbac-override-reloader' ? 'true' : 'false'

  await page.route('**/runtime-config.js', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/javascript; charset=utf-8',
      body: `window.__NOTEBOOK_CONFIG__ = {
  API_BASE_URL: "",
  AUTH_TRANSPORT: "bearer",
  ADMIN_UI_ENABLED: true,
  ADMIN_UI_DEV_OPEN: false,
  AUDIT_API_MODE: "mock",
  ENTERPRISE_ADMIN_WRITE_ENABLED: ${enterpriseWrite},
  ENTERPRISE_ADMIN_APPROVALS_ENABLED: true,
  ADMIN_RBAC_UI_ENABLED: ${rbacUi},
  ADMIN_RBAC_OVERRIDES_STATUS_ENABLED: ${rbacOverridesStatus},
  ADMIN_RBAC_OVERRIDES_RELOAD_ENABLED: ${rbacOverridesReload},
};
`,
    })
  })

  const userId = 'e2e-rbac-user'
  const baseUser = {
    id: userId,
    email: 'rbac-e2e@example.com',
    name: 'RBAC E2E',
    roles: ['ROLE_USER'] as string[],
  }

  let platformPermissions: string[] | undefined
  let platformRoles: string[] | undefined

  if (profile === 'audit-viewer') {
    platformPermissions = ['admin:audit:read']
  } else if (profile === 'rbac-reader') {
    platformPermissions = ['admin:rbac:read']
  } else if (profile === 'rbac-override-reloader') {
    platformPermissions = ['admin:rbac:read', 'admin:rbac:override:reload']
  } else if (profile === 'change-approver') {
    platformRoles = ['PLATFORM_CHANGE_REQUEST_APPROVER']
    platformPermissions = [
      'admin:enterprise:status:read',
      'admin:change-request:list',
      'admin:change-request:approve',
      'admin:change-request:reject',
    ]
  } else {
    platformPermissions = []
  }

  await page.route('**/auth/signup', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: 'e2e-fake-access',
        refreshToken: 'e2e-fake-refresh',
        tokenType: 'Bearer',
        expiresIn: 3600,
        user: { ...baseUser, roles: baseUser.roles, platformPermissions, platformRoles },
      }),
    })
  })

  await page.route('**/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        userId,
        email: baseUser.email,
        name: baseUser.name,
        roles: baseUser.roles,
        platformRoles,
        platformPermissions,
        avatarUrl: null,
      }),
    })
  })

  await page.route('**/workspaces?**', async (route) => {
    if (route.request().method() !== 'GET') {
      await route.continue()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: 'ws-e2e-rbac',
            slug: 'e2e-rbac',
            name: 'E2E Workspace',
            type: 'PERSONAL',
            ownerId: userId,
            createdAt: '2026-01-01T00:00:00Z',
            updatedAt: '2026-01-01T00:00:00Z',
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

  await page.route('**/workspaces/*/notebooks**', async (route) => {
    if (route.request().method() !== 'GET') {
      await route.continue()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
        hasPrevious: false,
      }),
    })
  })

  if (profile === 'change-approver') {
    await page.route(matchGatewayChangeRequestsApi, async (route) => {
      if (route.request().method() !== 'GET') {
        await route.continue()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              id: 'cr-e2e-1',
              requestedByUserId: 'other-approver-user',
              status: 'PENDING',
              operationType: 'ADMIN_MFA_MODE_UPDATE',
              targetService: 'gateway',
              targetKey: 'admin.mfa.mode',
              currentValue: 'off',
              requestedValue: 'enforce',
              severity: 'MEDIUM',
              impactSummary: { severity: 'MEDIUM', description: 'test', rollback: 'n/a' },
              validationResult: {},
              createdAt: '2026-05-01T12:00:00.000Z',
              externalRequestId: null,
              decidedAt: null,
              decidedByUserId: null,
              decisionReason: null,
              approvedAt: null,
              rejectedAt: null,
            },
          ],
        }),
      })
    },
    )
  }
}

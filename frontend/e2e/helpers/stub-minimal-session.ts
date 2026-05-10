import type { Page } from '@playwright/test'

/**
 * Satisfies signup → /app shell data fetches without a live gateway (bearer-mode E2E).
 * Routes match the browser-origin API host from `VITE_API_BASE_URL` (default localhost:8080).
 */
export async function stubMinimalAuthenticatedSession(
  page: Page,
  opts?: { signupRoles?: string[] },
) {
  await page.route('**/runtime-config.js', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/javascript; charset=utf-8',
      body: `window.__NOTEBOOK_CONFIG__ = {
  API_BASE_URL: "",
  AUTH_TRANSPORT: "bearer",
  ADMIN_UI_ENABLED: true,
  ADMIN_UI_DEV_OPEN: true,
  AUDIT_API_MODE: "mock",
};
`,
    })
  })

  const userId = 'e2e-admin-audit-user'
  const user = {
    id: userId,
    email: 'admin-audit-e2e@example.com',
    name: 'Admin Audit E2E',
    roles: (opts?.signupRoles ?? []) as string[],
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
        user,
      }),
    })
  })

  await page.route('**/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        userId,
        email: user.email,
        name: user.name,
        roles: user.roles,
        avatarUrl: null,
        ...(user.roles.length > 0 ? { platformRoles: user.roles } : {}),
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
            id: 'ws-e2e-admin-audit',
            slug: 'e2e-admin-audit',
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
}

import type { Page } from '@playwright/test'
import { stubMinimalAuthenticatedSession } from '../../../e2e/helpers/stub-minimal-session'

/** Authenticated user without platform admin roles; AdminGate must deny admin routes. */
export async function stubNonAdminSession(page: Page) {
  await stubMinimalAuthenticatedSession(page, { signupRoles: ['USER'] })
  await page.unroute('**/runtime-config.js')
  await page.route('**/runtime-config.js', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/javascript; charset=utf-8',
      body: `window.__NOTEBOOK_CONFIG__ = {
  API_BASE_URL: "",
  AUTH_TRANSPORT: "bearer",
  ADMIN_UI_ENABLED: true,
  ADMIN_UI_DEV_OPEN: false,
  AUDIT_API_MODE: "live",
};
`,
    })
  })
}

import { expect, test } from '@playwright/test'
import { signUpAndLogin } from './helpers/auth.helper'
import { stubMinimalAuthenticatedSession } from './helpers/stub-minimal-session'

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
  ADMIN_UI_DEV_OPEN: false,
  AUDIT_API_MODE: "real",
};
`,
    })
  })
  await page.route('**/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        userId: 'e2e-enterprise-user',
        email: 'enterprise-e2e@example.com',
        name: 'Enterprise E2E',
        roles: ['PLATFORM_ADMIN'],
        avatarUrl: null,
      }),
    })
  })
})

test('enterprise console shows mfa required state', async ({ page }) => {
  await page.route('**/admin/enterprise/status**', async (route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        status: 403,
        errorCode: 'ADMIN_MFA_REQUIRED',
        message: 'Admin access requires multi-factor authentication.',
        path: '/admin/enterprise/status',
      }),
    })
  })
  await signUpAndLogin(page, `enterprise-e2e-${Date.now()}@example.com`, 'Password1234!')
  await page.goto('/app/admin/enterprise')
  await expect(page.getByText('Admin access requires multi-factor authentication.')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Go to Security Settings' })).toBeVisible()
})

test('enterprise console shows permission denied on 403', async ({ page }) => {
  await page.route('**/admin/enterprise/status**', async (route) => {
    await route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: JSON.stringify({
        timestamp: new Date().toISOString(),
        status: 403,
        errorCode: 'ADMIN_ACCESS_DENIED',
        message: 'Admin access denied',
        path: '/admin/enterprise/status',
      }),
    })
  })
  await signUpAndLogin(page, `enterprise-e2e-${Date.now()}@example.com`, 'Password1234!')
  await page.goto('/app/admin/enterprise')
  await expect(page.getByText('Enterprise console restricted')).toBeVisible()
})

test('enterprise console renders mocked status and warning', async ({ page }) => {
  await page.route('**/admin/enterprise/status**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        environment: 'e2e',
        generatedAt: new Date().toISOString(),
        features: {
          sso: {
            enabled: true,
            providersConfigured: 1,
            allowedDomainsConfigured: false,
            adminGroupMappingConfigured: false,
            trustIdpMfa: false,
          },
          scim: { enabled: false, groupsEnabled: false, adminGroupsConfigured: false, tokenConfigured: false },
          mfa: { adminMfaMode: 'warn', acceptedMethods: ['webauthn'], identityMfaEnabled: true, webauthnEnabled: true },
          siem: { enabled: false, provider: 'noop', workerEnabled: false, endpointConfigured: false, secretConfigured: false },
          auditExport: {
            enabled: false,
            machineAuthEnabled: false,
            scheduledExportConfigured: false,
            archiveUploadEnabled: false,
            archiveProvider: '',
            machineAuthPublicKeyConfigured: false,
          },
          notifications: {
            sseEnabled: true,
            distributedFanoutEnabled: false,
            digestEnabled: true,
            digestWorkerEnabled: true,
          },
          gatewaySecurity: {
            adminEnabled: true,
            adminAuditEnabled: true,
            adminMfaMode: 'warn',
            adminMfaAcceptedMethods: ['webauthn'],
            rateLimitEnabled: true,
            csrfEnabled: false,
            authTransport: 'bearer',
            cookieModeEnabled: false,
          },
        },
        warnings: [
          {
            code: 'SSO_ADMIN_MAPPING_MISSING',
            message: 'SSO is enabled but admin group mapping is not configured.',
            severity: 'WARNING',
          },
        ],
        identityUnavailable: false,
        notificationUnavailable: false,
      }),
    })
  })
  await signUpAndLogin(page, `enterprise-e2e-${Date.now()}@example.com`, 'Password1234!')
  await page.goto('/app/admin/enterprise')
  await expect(page.getByRole('heading', { name: 'Enterprise console' })).toBeVisible()
  await expect(page.getByText('SSO_ADMIN_MAPPING_MISSING')).toBeVisible()
})

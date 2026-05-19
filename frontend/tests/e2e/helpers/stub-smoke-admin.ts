import type { Page } from '@playwright/test'
import { stubMinimalAuthenticatedSession } from '../../../e2e/helpers/stub-minimal-session'
import { matchGatewayChangeRequestsApi } from '../../../e2e/helpers/gateway-stub-urls'

export async function stubSmokeAdminSession(page: Page) {
  await stubMinimalAuthenticatedSession(page, { signupRoles: ['PLATFORM_ADMIN'] })
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
  ENTERPRISE_GITOPS_PR_ENABLED: true,
  GITOPS_RBAC_ROLE_REQUESTS_ENABLED: true,
  NOTIFICATION_ANALYTICS_UI_ENABLED: true,
  NOTIFICATION_DEAD_LETTER_UI_ENABLED: true,
  NOTIFICATION_RETENTION_UI_ENABLED: true,
  NOTIFICATION_RETENTION_PURGE_UI_ENABLED: false,
  NOTIFICATION_LEGAL_HOLD_UI_ENABLED: true,
  PLATFORM_RETENTION_GOVERNANCE_ENABLED: true,
  AUDIT_API_MODE: "mock",
};
`,
    })
  })

  await page.route('**/admin/notifications/analytics/**', async (route) => {
    if (route.request().method() !== 'GET' || route.request().resourceType() === 'document') {
      await route.continue()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        from: '2026-01-01T00:00:00Z',
        to: '2026-01-02T00:00:00Z',
        bucket: 'hour',
        totals: {
          created: 1,
          sent: 1,
          failed: 0,
          dead: 0,
          skippedPreference: 0,
          digestQueued: 0,
          digestSent: 0,
          quietHoursDelayed: 0,
        },
        byChannel: [],
        byType: [],
        fanout: { pending: 0, retrying: 0, dead: 0 },
        sse: { activeConnections: 0, sendFailuresInRange: 0, eventsSentMeterTotal: 0 },
        redisFanout: { publishSuccessInRange: 0, publishFailureInRange: 0, subscriberReceivedInRange: 0 },
        digest: { pendingItems: 0, workerEnabled: true, digestEnabled: true },
        workers: {
          fanoutWorkerLastRun: null,
          digestWorkerLastRun: null,
          emailWorkerLastRun: null,
          fanoutWorkerEnabled: true,
          emailWorkerEnabled: true,
        },
      }),
    })
  })

  await page.route('**/admin/notifications/dead-letter?**', async (route) => {
    if (route.request().method() !== 'GET' || route.request().resourceType() === 'document') {
      await route.continue()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: [], page: 0, size: 20, totalElements: 0 }),
    })
  })

  await page.route('**/admin/notifications/retention/**', async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          generatedAt: '2026-01-01T00:00:00Z',
          dryRun: true,
          targets: [],
          warnings: [],
        }),
      })
      return
    }
    await route.continue()
  })

  await page.route('**/admin/retention/platform/**', async (route) => {
    if (route.request().method() !== 'GET') {
      await route.continue()
      return
    }
    const url = route.request().url()
    if (url.includes('legal-holds')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      })
      return
    }
    if (url.includes('targets')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ generatedAt: '2026-01-01T00:00:00Z', targets: [] }),
      })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        generatedAt: '2026-01-01T00:00:00Z',
        dryRun: true,
        targets: [],
        warnings: [],
        serviceSummaries: [],
      }),
    })
  })

  await page.route((url) => matchGatewayChangeRequestsApi(new URL(url)), async (route) => {
    if (route.request().method() !== 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [] }) })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            id: 'cr-smoke-1',
            requestedByUserId: 'e2e-admin-audit-user',
            status: 'APPROVED',
            operationType: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
            targetService: 'content',
            targetKey: 'KEY',
            currentValue: null,
            requestedValue: 'true',
            severity: 'MEDIUM',
            impactSummary: { severity: 'MEDIUM', description: 'smoke' },
            validationResult: {},
            createdAt: '2026-01-01T00:00:00Z',
            externalRequestId: null,
            decidedAt: null,
            decidedByUserId: null,
            decisionReason: null,
            approvedAt: null,
            rejectedAt: null,
            targetEnvironment: 'staging',
          },
        ],
      }),
    })
  })
}

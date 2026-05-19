import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi, afterEach } from 'vitest'
import { AdminNotificationAnalyticsPage } from './admin/AdminNotificationAnalyticsPage'
import { AdminNotificationDeadLetterPage } from './admin/AdminNotificationDeadLetterPage'
import { AdminNotificationDeadLetterDetailPage } from './admin/AdminNotificationDeadLetterDetailPage'
import { AdminNotificationDeadLetterRequeuePage } from './admin/AdminNotificationDeadLetterRequeuePage'
import { AdminNotificationRetentionPage } from './admin/AdminNotificationRetentionPage'
import { AdminPlatformRetentionPage } from './admin/AdminPlatformRetentionPage'
import { AdminPlatformLegalHoldsPage } from './admin/AdminPlatformLegalHoldsPage'
import { AdminPurgeResultPage } from './admin/AdminPurgeResultPage'
import { AdminRetentionHubPage } from './admin/AdminRetentionHubPage'
import { PurgeConfirmationDialog } from '../features/admin/retention/PurgeConfirmationDialog'
import { PurgeResultSummary } from '../features/admin/retention/PurgeResultSummary'
import * as analyticsApi from '../features/admin/notification-analytics-api'
import * as deadLetterApi from '../features/admin/notification-dead-letter-api'
import * as retentionApi from '../features/admin/notification-retention-api'
import * as platformApi from '../features/admin/platform-retention-api'

const adminUser = {
  id: 'admin-1',
  roles: ['PLATFORM_ADMIN'],
  platformPermissions: [
    'admin:notifications:analytics:read',
    'admin:notifications:dead-letter:read',
    'admin:notifications:dead-letter:requeue',
    'admin:notifications:retention:read',
    'admin:notifications:retention:run',
    'admin:notifications:legal-hold:read',
    'admin:retention:read',
    'admin:retention:legal-hold:write',
  ],
}

vi.mock('../features/auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: unknown }) => unknown) => selector({ user: adminUser }),
}))

vi.mock('../shared/config/admin-feature-flags', () => ({
  isNotificationAnalyticsUiEnabled: () => true,
  isNotificationDeadLetterUiEnabled: () => true,
  isNotificationRetentionUiEnabled: () => true,
  isNotificationRetentionPurgeUiEnabled: () => false,
  isNotificationLegalHoldUiEnabled: () => true,
  isPlatformRetentionGovernanceUiEnabled: () => true,
}))

const deadLetterRow: deadLetterApi.DeadLetterListItem = {
  id: 'dl-1',
  source: 'FANOUT_OUTBOX',
  eventType: 'notification.created',
  recipientUserIdHash: 'abcdef1234567890',
  status: 'DEAD',
  attemptCount: 3,
  requeueCount: 0,
  lastErrorCode: 'DELIVERY_FAILED',
  lastErrorSummary: 'timeout',
  createdAt: '2026-05-10T00:00:00Z',
  updatedAt: '2026-05-10T01:00:00Z',
  deadAt: '2026-05-10T01:00:00Z',
}

describe('Faz 144 notification ops & retention', () => {
  afterEach(() => vi.restoreAllMocks())

  it('renders notification analytics delivery health cards', async () => {
    vi.spyOn(analyticsApi, 'fetchNotificationAnalyticsSummary').mockResolvedValue({
      from: '2026-05-09T00:00:00Z',
      to: '2026-05-10T00:00:00Z',
      bucket: 'hour',
      totals: {
        created: 10,
        sent: 8,
        failed: 1,
        dead: 1,
        skippedPreference: 2,
        digestQueued: 1,
        digestSent: 1,
        quietHoursDelayed: 0,
      },
      fanout: { pending: 0, retrying: 0, dead: 1 },
      sse: { activeConnections: 1, sendFailuresInRange: 0, eventsSentMeterTotal: 5 },
      redisFanout: { publishSuccessInRange: 1, publishFailureInRange: 0, subscriberReceivedInRange: 1 },
      digest: { pendingItems: 0, workerEnabled: true, digestEnabled: true },
      workers: {
        fanoutWorkerLastRun: null,
        digestWorkerLastRun: null,
        emailWorkerLastRun: null,
        fanoutWorkerEnabled: true,
        emailWorkerEnabled: true,
      },
      byChannel: [{ channel: 'IN_APP', created: 5, queued: 0, sent: 5, failed: 0 }],
      byType: [{ notificationType: 'mention', created: 5 }],
    })
    render(
      <MemoryRouter>
        <AdminNotificationAnalyticsPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('delivery-health-cards')).toBeTruthy())
    expect(screen.getAllByTestId('admin-metric-card').length).toBeGreaterThan(0)
    expect(document.body.textContent).not.toMatch(/Bearer\s+/i)
    expect(document.body.textContent).not.toContain('abcdef1234567890')
  })

  it('renders dead-letter queue table', async () => {
    vi.spyOn(deadLetterApi, 'fetchDeadLetterList').mockResolvedValue({
      items: [deadLetterRow],
      page: 0,
      size: 20,
      totalElements: 1,
    })
    render(
      <MemoryRouter>
        <AdminNotificationDeadLetterPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('dead-letter-event-table')).toBeTruthy())
    expect(screen.getByText('notification.created')).toBeTruthy()
    expect(document.body.textContent).not.toContain('abcdef1234567890')
  })

  it('renders dead-letter detail sanitized', async () => {
    vi.spyOn(deadLetterApi, 'fetchDeadLetterList').mockResolvedValue({
      items: [deadLetterRow],
      page: 0,
      size: 20,
      totalElements: 1,
    })
    vi.spyOn(deadLetterApi, 'dryRunDeadLetterRequeue').mockResolvedValue({
      id: 'dl-1',
      canRequeue: true,
      source: 'FANOUT_OUTBOX',
      impact: { severity: 'LOW', duplicateRisk: 'LOW', reason: 'ok' },
      checks: [{ code: 'STATUS_DEAD', passed: true }],
    })
    render(
      <MemoryRouter initialEntries={['/app/admin/notifications/dead-letter/dl-1']}>
        <Routes>
          <Route path="/app/admin/notifications/dead-letter/:eventId" element={<AdminNotificationDeadLetterDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('dead-letter-event-detail')).toBeTruthy())
    expect(document.body.textContent).not.toContain('abcdef1234567890')
    expect(document.body.textContent).not.toMatch(/password|Bearer|eyJ/i)
  })

  it('renders requeue workflow dry-run state', async () => {
    vi.spyOn(deadLetterApi, 'fetchDeadLetterList').mockResolvedValue({
      items: [deadLetterRow],
      page: 0,
      size: 20,
      totalElements: 1,
    })
    vi.spyOn(deadLetterApi, 'dryRunDeadLetterRequeue').mockResolvedValue({
      id: 'dl-1',
      canRequeue: true,
      source: 'FANOUT_OUTBOX',
      impact: { severity: 'LOW', duplicateRisk: 'HIGH', reason: 'duplicate risk' },
      checks: [{ code: 'STATUS_DEAD', passed: true }],
    })
    render(
      <MemoryRouter initialEntries={['/app/admin/notifications/dead-letter/dl-1/requeue']}>
        <Routes>
          <Route
            path="/app/admin/notifications/dead-letter/:eventId/requeue"
            element={<AdminNotificationDeadLetterRequeuePage />}
          />
        </Routes>
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('dead-letter-requeue-workflow')).toBeTruthy())
    expect(screen.getByTestId('requeue-eligibility-checklist')).toBeTruthy()
    expect(screen.getByTestId('duplicate-risk-badge')).toBeTruthy()
  })

  it('renders notification retention overview', async () => {
    vi.spyOn(retentionApi, 'fetchRetentionPlan').mockResolvedValue({
      generatedAt: '2026-05-10T00:00:00Z',
      dryRun: true,
      targets: [
        {
          target: 'fanout_dead',
          eligibleCount: 5,
          retention: '30d',
          oldestEligibleAt: null,
          cutoff: '2026-04-10T00:00:00Z',
          blockedByLegalHold: true,
          activeHoldKeys: ['hold-1'],
          purgeableCount: 0,
        },
      ],
      warnings: ['QUERY_CAPPED'],
    })
    render(
      <MemoryRouter>
        <AdminNotificationRetentionPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('retention-target-table')).toBeTruthy())
    expect(screen.getByText(/Blocked \(hold-1\)/i)).toBeTruthy()
  })

  it('renders platform retention governance', async () => {
    vi.spyOn(platformApi, 'fetchPlatformRetentionTargets').mockResolvedValue({
      generatedAt: '2026-05-10T00:00:00Z',
      targets: [
        {
          targetKey: 'content.notes',
          service: 'content',
          displayName: 'Notes',
          description: 'desc',
          dataClass: 'CONTENT',
          defaultRetentionDays: 365,
          legalHoldSupported: true,
          destructivePurgeSupported: false,
          dryRunSupported: true,
          archiveRequiredBeforePurge: false,
          riskLevel: 'MEDIUM',
          status: 'DRY_RUN_READY',
        },
      ],
    })
    vi.spyOn(platformApi, 'fetchPlatformRetentionPlan').mockResolvedValue({
      generatedAt: '2026-05-10T00:00:00Z',
      dryRun: true,
      targets: [],
      warnings: [],
      serviceSummaries: [
        {
          service: 'content',
          dataClass: 'CONTENT',
          status: 'READY',
          totalTargets: 1,
          dryRunReadyTargets: 1,
          inventoryOnlyTargets: 0,
          unavailableTargets: 0,
          blockedTargets: 0,
          cappedTargets: 0,
          warningCount: 0,
          warnings: [],
        },
      ],
    })
    vi.spyOn(platformApi, 'fetchPlatformLegalHolds').mockResolvedValue({ items: [] })
    render(
      <MemoryRouter>
        <AdminPlatformRetentionPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByText(/Platform retention/i)).toBeTruthy())
  })

  it('renders legal holds management', async () => {
    vi.spyOn(platformApi, 'fetchPlatformLegalHolds').mockResolvedValue({
      items: [
        {
          id: 'h1',
          holdKey: 'litigation-2026',
          scope: 'ALL_PLATFORM',
          scopeRefPresent: false,
          status: 'ACTIVE',
          createdByUserId: 'user-long-id-12345',
          createdAt: '2026-05-01T00:00:00Z',
          expiresAt: null,
        },
      ],
    })
    render(
      <MemoryRouter>
        <AdminPlatformLegalHoldsPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('legal-holds-management')).toBeTruthy())
    expect(screen.getByTestId('legal-hold-card')).toBeTruthy()
    expect(document.body.textContent).not.toContain('user-long-id-12345')
  })

  it('purge confirmation dialog is disabled when purge flag off', () => {
    render(
      <PurgeConfirmationDialog
        open
        reason="valid reason here"
        confirmText="DELETE"
        busy={false}
        purgeEnabled={false}
        onReasonChange={() => {}}
        onConfirmTextChange={() => {}}
        onConfirm={() => {}}
        onCancel={() => {}}
      />,
    )
    expect(screen.getByTestId('purge-confirmation-dialog')).toBeTruthy()
    expect(screen.getByRole('button', { name: /Execute purge/i })).toBeDisabled()
  })

  it('renders purge result summary', () => {
    render(
      <PurgeResultSummary
        payload={{
          requestId: 'req-1',
          recordedAt: '2026-05-10T00:00:00Z',
          actorLabel: 'admi…n-1',
          result: {
            dryRun: false,
            target: 'ALL',
            totalDeleted: 3,
            deletedByTarget: { fanout_dead: 3 },
            skippedByLegalHold: 1,
            legalHoldKeysBlocking: ['hold-1'],
            planSnapshot: { generatedAt: '', dryRun: true, targets: [], warnings: [] },
          },
        }}
      />,
    )
    expect(screen.getByTestId('purge-result-summary')).toBeTruthy()
    expect(screen.getByText('req-1')).toBeTruthy()
  })

  it('renders retention hub and purge result empty state', () => {
    render(
      <MemoryRouter>
        <AdminRetentionHubPage />
      </MemoryRouter>,
    )
    expect(screen.getByTestId('admin-retention-hub')).toBeTruthy()
    render(
      <MemoryRouter>
        <AdminPurgeResultPage />
      </MemoryRouter>,
    )
    expect(screen.getByText(/No purge result/i)).toBeTruthy()
  })
})


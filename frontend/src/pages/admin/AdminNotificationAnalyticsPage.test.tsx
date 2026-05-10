import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { AdminNotificationAnalyticsPage } from './AdminNotificationAnalyticsPage'
import * as analyticsApi from '../../features/admin/notification-analytics-api'

const summary = {
  from: '2026-05-09T00:00:00Z',
  to: '2026-05-10T00:00:00Z',
  bucket: 'hour',
  totals: {
    created: 10,
    sent: 8,
    failed: 1,
    dead: 0,
    skippedPreference: 2,
    digestQueued: 1,
    digestSent: 1,
    quietHoursDelayed: 0,
  },
  byChannel: [
    { channel: 'IN_APP', created: 7, queued: 0, sent: 7, failed: 0 },
    { channel: 'EMAIL', created: 3, queued: 1, sent: 1, failed: 1 },
  ],
  byType: [{ notificationType: 'COMMENT_ADDED', created: 5 }],
  fanout: { pending: 0, retrying: 0, dead: 0 },
  sse: { activeConnections: 2, sendFailuresInRange: 0, eventsSentMeterTotal: 100 },
  redisFanout: { publishSuccessInRange: 0, publishFailureInRange: 0, subscriberReceivedInRange: 0 },
  digest: { pendingItems: 0, workerEnabled: true, digestEnabled: true },
  workers: {
    fanoutWorkerLastRun: null,
    digestWorkerLastRun: null,
    emailWorkerLastRun: null,
    fanoutWorkerEnabled: true,
    emailWorkerEnabled: true,
  },
}

const { mockUseAuthStore } = vi.hoisted(() => ({
  mockUseAuthStore: vi.fn(),
}))

vi.mock('../../shared/config/admin-feature-flags', () => ({
  isNotificationAnalyticsUiEnabled: () => true,
  isNotificationDeadLetterUiEnabled: () => false,
  isNotificationRetentionUiEnabled: () => false,
  isNotificationLegalHoldUiEnabled: () => false,
}))

vi.mock('../../features/auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: unknown }) => unknown) => mockUseAuthStore(selector),
}))

describe('AdminNotificationAnalyticsPage', () => {
  beforeEach(() => {
    mockUseAuthStore.mockReset()
  })

  it('shows permission message when user lacks analytics permission', () => {
    mockUseAuthStore.mockImplementation((selector) =>
      selector({
        user: { id: 'u1', roles: ['USER'], platformPermissions: [] },
      }),
    )
    render(
      <MemoryRouter>
        <AdminNotificationAnalyticsPage />
      </MemoryRouter>,
    )
    expect(
      screen.getByText(/You do not have permission to view notification analytics/i),
    ).toBeInTheDocument()
  })

  it('loads summary and shows totals when platform admin', async () => {
    mockUseAuthStore.mockImplementation((selector) =>
      selector({
        user: { id: 'a1', roles: ['PLATFORM_ADMIN'], platformPermissions: [] },
      }),
    )
    vi.spyOn(analyticsApi, 'fetchNotificationAnalyticsSummary').mockResolvedValue(summary)
    render(
      <MemoryRouter>
        <AdminNotificationAnalyticsPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(analyticsApi.fetchNotificationAnalyticsSummary).toHaveBeenCalled())
    expect(screen.getByText('10')).toBeInTheDocument()
    expect(screen.getByText(/Fanout outbox/i)).toBeInTheDocument()
  })
})

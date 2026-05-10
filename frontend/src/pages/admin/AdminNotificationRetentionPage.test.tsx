import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { AdminNotificationRetentionPage } from './AdminNotificationRetentionPage'
import * as retentionApi from '../../features/admin/notification-retention-api'

const { mockUseAuthStore } = vi.hoisted(() => ({
  mockUseAuthStore: vi.fn(),
}))

vi.mock('../../shared/config/admin-feature-flags', () => ({
  isNotificationAnalyticsUiEnabled: () => false,
  isNotificationRetentionUiEnabled: () => true,
  isNotificationRetentionPurgeUiEnabled: () => false,
  isNotificationLegalHoldUiEnabled: () => false,
}))

vi.mock('../../features/auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: unknown }) => unknown) => mockUseAuthStore(selector),
}))

describe('AdminNotificationRetentionPage', () => {
  beforeEach(() => {
    mockUseAuthStore.mockReset()
    vi.restoreAllMocks()
  })

  it('shows permission message without read permission', () => {
    mockUseAuthStore.mockImplementation((selector) =>
      selector({
        user: { id: 'u1', roles: ['USER'], platformPermissions: [] },
      }),
    )
    render(
      <MemoryRouter>
        <AdminNotificationRetentionPage />
      </MemoryRouter>,
    )
    expect(screen.getByText(/do not have permission to view retention plans/i)).toBeInTheDocument()
  })

  it('renders plan table when allowed', async () => {
    mockUseAuthStore.mockImplementation((selector) =>
      selector({
        user: { id: 'a1', roles: ['PLATFORM_ADMIN'], platformPermissions: [] },
      }),
    )
    vi.spyOn(retentionApi, 'fetchRetentionPlan').mockResolvedValue({
      generatedAt: '2026-05-10T00:00:00Z',
      dryRun: true,
      targets: [
        {
          target: 'notification_delivery_analytics_hourly',
          eligibleCount: 1,
          retention: '90d',
          oldestEligibleAt: null,
          cutoff: '2026-01-01T00:00:00Z',
          blockedByLegalHold: false,
          activeHoldKeys: [],
          purgeableCount: 1,
          targetWarnings: [],
        },
      ],
      warnings: [],
    })
    render(
      <MemoryRouter>
        <AdminNotificationRetentionPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(retentionApi.fetchRetentionPlan).toHaveBeenCalled())
    expect(screen.getByText('notification_delivery_analytics_hourly')).toBeInTheDocument()
  })
})

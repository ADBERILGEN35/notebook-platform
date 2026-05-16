import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { AdminPlatformRetentionPage } from './AdminPlatformRetentionPage'
import * as platformRetentionApi from '../../features/admin/platform-retention-api'

const { mockUseAuthStore } = vi.hoisted(() => ({
  mockUseAuthStore: vi.fn(),
}))

vi.mock('../../shared/config/admin-feature-flags', () => ({
  isPlatformRetentionGovernanceUiEnabled: () => true,
}))

vi.mock('../../features/auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: unknown }) => unknown) => mockUseAuthStore(selector),
}))

describe('AdminPlatformRetentionPage', () => {
  beforeEach(() => {
    mockUseAuthStore.mockReset()
    vi.restoreAllMocks()
    mockUseAuthStore.mockImplementation((selector) =>
      selector({
        user: {
          id: 'admin-1',
          roles: ['PLATFORM_SECURITY_ADMIN'],
          platformPermissions: ['admin:retention:read', 'admin:retention:legal-hold:write'],
        },
      }),
    )
  })

  it('renders target inventory, dry-run plan, and legal hold status', async () => {
    vi.spyOn(platformRetentionApi, 'fetchPlatformRetentionTargets').mockResolvedValue({
      generatedAt: '2026-05-13T00:00:00Z',
      targets: [
        {
          targetKey: 'content.note_versions',
          service: 'content-service',
          displayName: 'Note versions',
          description: 'Version history',
          dataClass: 'CONTENT',
          defaultRetentionDays: 365,
          legalHoldSupported: true,
          destructivePurgeSupported: false,
          dryRunSupported: true,
          archiveRequiredBeforePurge: true,
          riskLevel: 'HIGH',
          status: 'INVENTORY_ONLY',
        },
      ],
    })
    vi.spyOn(platformRetentionApi, 'fetchPlatformRetentionPlan').mockResolvedValue({
      generatedAt: '2026-05-13T00:00:00Z',
      dryRun: true,
      warnings: ['Platform retention governance is dry-run only.'],
      targets: [
        {
          targetKey: 'content.note_versions',
          service: 'content-service',
          status: 'INVENTORY_ONLY',
          defaultRetentionDays: 365,
          eligibleCount: null,
          purgeableCount: 0,
          blockedByLegalHold: true,
          activeHoldKeys: ['hold-all'],
          riskLevel: 'HIGH',
          warnings: ['Inventory only. Destructive purge is not implemented.'],
        },
      ],
    })
    vi.spyOn(platformRetentionApi, 'fetchPlatformLegalHolds').mockResolvedValue({
      items: [
        {
          id: '00000000-0000-0000-0000-000000000001',
          holdKey: 'hold-all',
          scope: 'ALL_PLATFORM',
          scopeRefPresent: false,
          status: 'ACTIVE',
          createdByUserId: 'admin-1',
          createdAt: '2026-05-13T00:00:00Z',
          releasedAt: null,
          expiresAt: null,
        },
      ],
    })

    render(<AdminPlatformRetentionPage />)

    await waitFor(() => expect(platformRetentionApi.fetchPlatformRetentionTargets).toHaveBeenCalled())
    expect(screen.getByText('Note versions')).toBeInTheDocument()
    expect(screen.getAllByText('content.note_versions')).toHaveLength(2)
    expect(screen.getByText('Blocked (hold-all)')).toBeInTheDocument()
    expect(screen.getAllByText('hold-all')).toHaveLength(2)
    expect(screen.getByText('Disabled')).toBeInTheDocument()
  })

  it('renders DRY_RUN_READY badge for content targets when registry reports dry-run ready', async () => {
    vi.spyOn(platformRetentionApi, 'fetchPlatformRetentionTargets').mockResolvedValue({
      generatedAt: '2026-05-15T00:00:00Z',
      targets: [
        {
          targetKey: 'content.note_versions',
          service: 'content-service',
          displayName: 'Note versions',
          description: 'Version history',
          dataClass: 'CONTENT',
          defaultRetentionDays: 365,
          legalHoldSupported: true,
          destructivePurgeSupported: false,
          dryRunSupported: true,
          archiveRequiredBeforePurge: true,
          riskLevel: 'HIGH',
          status: 'DRY_RUN_READY',
        },
        {
          targetKey: 'content.search_documents',
          service: 'content-service',
          displayName: 'Content search documents',
          description: 'Outbox-derived rows',
          dataClass: 'CONTENT',
          defaultRetentionDays: 90,
          legalHoldSupported: true,
          destructivePurgeSupported: false,
          dryRunSupported: true,
          archiveRequiredBeforePurge: false,
          riskLevel: 'MEDIUM',
          status: 'DRY_RUN_READY',
        },
      ],
    })
    vi.spyOn(platformRetentionApi, 'fetchPlatformRetentionPlan').mockResolvedValue({
      generatedAt: '2026-05-15T00:00:00Z',
      dryRun: true,
      warnings: [],
      targets: [
        {
          targetKey: 'content.note_versions',
          service: 'content-service',
          status: 'DRY_RUN_READY',
          defaultRetentionDays: 365,
          eligibleCount: 1200,
          purgeableCount: 1200,
          blockedByLegalHold: false,
          activeHoldKeys: [],
          riskLevel: 'HIGH',
          warnings: [],
        },
        {
          targetKey: 'content.search_documents',
          service: 'content-service',
          status: 'DRY_RUN_READY',
          defaultRetentionDays: 90,
          eligibleCount: 420,
          purgeableCount: 420,
          blockedByLegalHold: false,
          activeHoldKeys: [],
          riskLevel: 'MEDIUM',
          warnings: [],
        },
      ],
    })
    vi.spyOn(platformRetentionApi, 'fetchPlatformLegalHolds').mockResolvedValue({ items: [] })

    render(<AdminPlatformRetentionPage />)

    await waitFor(() => expect(platformRetentionApi.fetchPlatformRetentionTargets).toHaveBeenCalled())
    expect(screen.getAllByText('DRY_RUN_READY').length).toBeGreaterThanOrEqual(2)
    expect(screen.getAllByText('1200').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('420').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Unavailable').length).toBeGreaterThanOrEqual(2)
    expect(screen.queryByText('Future gated')).not.toBeInTheDocument()
  })

  it('renders notification targets and notification service-unavailable warning', async () => {
    vi.spyOn(platformRetentionApi, 'fetchPlatformRetentionTargets').mockResolvedValue({
      generatedAt: '2026-05-16T00:00:00Z',
      targets: [
        {
          targetKey: 'notification.fanout_outbox_sent',
          service: 'notification-service',
          displayName: 'Fanout outbox (sent)',
          description: 'Terminal SENT fanout outbox rows',
          dataClass: 'NOTIFICATION',
          defaultRetentionDays: 7,
          legalHoldSupported: true,
          destructivePurgeSupported: false,
          dryRunSupported: true,
          archiveRequiredBeforePurge: false,
          riskLevel: 'MEDIUM',
          status: 'DRY_RUN_READY',
        },
      ],
    })
    vi.spyOn(platformRetentionApi, 'fetchPlatformRetentionPlan').mockResolvedValue({
      generatedAt: '2026-05-16T00:00:00Z',
      dryRun: true,
      warnings: ['NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE'],
      targets: [
        {
          targetKey: 'notification.fanout_outbox_sent',
          service: 'notification-service',
          status: 'DRY_RUN_READY',
          defaultRetentionDays: 7,
          eligibleCount: 340,
          purgeableCount: 340,
          blockedByLegalHold: false,
          activeHoldKeys: [],
          riskLevel: 'MEDIUM',
          warnings: ['NOTIFICATION_RETENTION_QUERY_CAPPED'],
          cutoff: '2026-05-09T00:00:00Z',
        },
      ],
    })
    vi.spyOn(platformRetentionApi, 'fetchPlatformLegalHolds').mockResolvedValue({ items: [] })

    render(<AdminPlatformRetentionPage />)

    await waitFor(() => expect(platformRetentionApi.fetchPlatformRetentionTargets).toHaveBeenCalled())
    expect(screen.getByText('Fanout outbox (sent)')).toBeInTheDocument()
    expect(screen.getByText('notification-service')).toBeInTheDocument()
    expect(screen.getByText('NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE')).toBeInTheDocument()
    expect(screen.getByText('NOTIFICATION_RETENTION_QUERY_CAPPED')).toBeInTheDocument()
    expect(screen.getAllByText('340').length).toBeGreaterThanOrEqual(1)
  })

  it('validates create legal hold reason before calling the API', async () => {
    vi.spyOn(platformRetentionApi, 'fetchPlatformRetentionTargets').mockResolvedValue({
      generatedAt: '2026-05-13T00:00:00Z',
      targets: [],
    })
    vi.spyOn(platformRetentionApi, 'fetchPlatformRetentionPlan').mockResolvedValue({
      generatedAt: '2026-05-13T00:00:00Z',
      dryRun: true,
      warnings: [],
      targets: [],
    })
    vi.spyOn(platformRetentionApi, 'fetchPlatformLegalHolds').mockResolvedValue({ items: [] })
    const createSpy = vi
      .spyOn(platformRetentionApi, 'createPlatformLegalHold')
      .mockResolvedValue({} as Awaited<ReturnType<typeof platformRetentionApi.createPlatformLegalHold>>)

    render(<AdminPlatformRetentionPage />)

    await waitFor(() => expect(platformRetentionApi.fetchPlatformRetentionTargets).toHaveBeenCalled())
    fireEvent.change(screen.getByPlaceholderText('holdKey'), { target: { value: 'hold-short-reason' } })
    fireEvent.change(screen.getByPlaceholderText('Reason'), { target: { value: 'short' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create hold' }))

    expect(await screen.findByText('Reason must be at least 10 characters.')).toBeInTheDocument()
    expect(createSpy).not.toHaveBeenCalled()
  })
})

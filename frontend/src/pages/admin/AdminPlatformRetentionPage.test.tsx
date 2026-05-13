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

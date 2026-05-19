import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi, afterEach } from 'vitest'
import { AdminEnterpriseChangeRequestsPage } from './AdminEnterpriseChangeRequestsPage'
import { AdminChangeRequestDetailPage } from './AdminChangeRequestDetailPage'
import { AdminChangeRequestDryRunPage } from './AdminChangeRequestDryRunPage'
import * as changeRequestsApi from '../../features/admin/enterprise/change-requests-api'

vi.mock('../../shared/config/admin-feature-flags', () => ({
  isEnterpriseAdminWriteEnabled: () => true,
  isEnterpriseAdminApprovalsUiEnabled: () => true,
  isEnterpriseGitOpsPrUiEnabled: () => true,
  isEnterpriseGitOpsRbacRoleRequestsUiEnabled: () => true,
}))

vi.mock('../../features/auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: Record<string, unknown> | null }) => unknown) =>
    selector({
      user: {
        id: 'creator-1',
        email: 'a@b.com',
        name: 'A',
        roles: ['PLATFORM_ADMIN'],
        platformPermissions: [
          'admin:change-request:list',
          'admin:change-request:approve',
          'admin:change-request:gitops:dry-run',
        ],
      },
    }),
}))

const baseItem = {
  targetService: 'content-service',
  targetKey: 'NOTE_MERGE_ANALYSIS_ENABLED',
  currentValue: null,
  requestedValue: 'true',
  severity: 'MEDIUM',
  impactSummary: { severity: 'MEDIUM', description: 'd' },
  validationResult: {},
  createdAt: new Date().toISOString(),
  externalRequestId: null,
  decidedAt: null,
  decidedByUserId: null,
  decisionReason: null,
  approvedAt: null,
  rejectedAt: null,
  targetEnvironment: 'staging',
}

function renderRoutes(initial = '/app/admin/change-requests') {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route path="/app/admin/change-requests" element={<AdminEnterpriseChangeRequestsPage />} />
        <Route path="/app/admin/change-requests/:id" element={<AdminChangeRequestDetailPage />} />
        <Route path="/app/admin/change-requests/:id/dry-run" element={<AdminChangeRequestDryRunPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdminEnterpriseChangeRequestsPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows approve disabled when request was created by current user', async () => {
    vi.spyOn(changeRequestsApi, 'listChangeRequests').mockResolvedValue({
      items: [
        {
          id: 'r1',
          requestedByUserId: 'creator-1',
          status: 'PENDING',
          operationType: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
          ...baseItem,
        },
      ],
    })
    const user = userEvent.setup()
    renderRoutes()
    await waitFor(() => expect(screen.getByRole('link', { name: 'View' })).toBeInTheDocument())
    await user.click(screen.getByRole('link', { name: 'View' }))
    const approveBtn = await screen.findByRole('button', { name: 'Approve' })
    expect(approveBtn).toBeDisabled()
    expect(approveBtn).toHaveAttribute('title', 'Another admin must approve')
  })

  it('approve dialog shows impact and calls API', async () => {
    vi.spyOn(changeRequestsApi, 'listChangeRequests').mockResolvedValue({
      items: [
        {
          id: 'r2',
          requestedByUserId: 'other-admin',
          status: 'PENDING',
          operationType: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
          ...baseItem,
          impactSummary: { severity: 'MEDIUM', description: 'impact text', rollback: 'rb' },
        },
      ],
    })
    const approveSpy = vi.spyOn(changeRequestsApi, 'approveChangeRequest').mockResolvedValue({
      id: 'r2',
      status: 'APPROVED',
      decidedAt: new Date().toISOString(),
      decidedByUserId: 'creator-1',
      operationType: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
      targetService: 'content-service',
      targetKey: 'NOTE_MERGE_ANALYSIS_ENABLED',
      requestedValue: 'true',
      nextStep: { type: 'GITOPS_OR_MANUAL_APPLY', message: 'next' },
    })
    const user = userEvent.setup()
    renderRoutes()
    await user.click(await screen.findByRole('link', { name: 'View' }))
    await user.click(await screen.findByRole('button', { name: 'Approve' }))
    expect(screen.getAllByText(/No runtime mutation/i).length).toBeGreaterThan(0)
    expect(document.body.textContent).toContain('impact text')
    await user.click(screen.getByRole('button', { name: 'Confirm approve' }))
    await waitFor(() => expect(approveSpy).toHaveBeenCalledWith('r2', { reason: undefined }))
  })

  it('status filter requests list with status query', async () => {
    const listSpy = vi.spyOn(changeRequestsApi, 'listChangeRequests').mockResolvedValue({ items: [] })
    const user = userEvent.setup()
    renderRoutes()
    await waitFor(() => expect(listSpy).toHaveBeenCalledWith('PENDING'))
    await user.click(screen.getByRole('button', { name: 'Approved' }))
    await waitFor(() => expect(listSpy).toHaveBeenCalledWith('APPROVED'))
  })

  it('approved request navigates to dry-run and runs preview', async () => {
    vi.spyOn(changeRequestsApi, 'listChangeRequests').mockResolvedValue({
      items: [
        {
          id: 'r3',
          requestedByUserId: 'other-admin',
          status: 'APPROVED',
          operationType: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
          ...baseItem,
        },
      ],
    })
    const drySpy = vi.spyOn(changeRequestsApi, 'gitopsDryRun').mockResolvedValue({
      changeRequestId: 'r3',
      targetEnvironment: 'staging',
      provider: 'mock',
      changedFiles: [],
      diffPreview: '--- preview',
      warnings: [],
    })
    const user = userEvent.setup()
    renderRoutes()
    await user.click(await screen.findByRole('link', { name: 'Dry-run GitOps' }))
    await user.click(await screen.findByRole('button', { name: 'Run dry-run' }))
    await waitFor(() => expect(drySpy).toHaveBeenCalledWith('r3', { targetEnvironment: 'staging' }))
    expect(await screen.findByText('--- preview')).toBeInTheDocument()
  })
})

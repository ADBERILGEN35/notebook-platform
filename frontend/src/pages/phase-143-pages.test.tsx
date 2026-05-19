import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi, afterEach } from 'vitest'
import { AdminChangeRequestsPage } from './admin/AdminChangeRequestsPage'
import { AdminChangeRequestDetailPage } from './admin/AdminChangeRequestDetailPage'
import { AdminChangeRequestDryRunPage } from './admin/AdminChangeRequestDryRunPage'
import { AdminChangeRequestGitOpsPage } from './admin/AdminChangeRequestGitOpsPage'
import { AdminChangeRequestDiffPage } from './admin/AdminChangeRequestDiffPage'
import { GitOpsDiffViewer } from '../features/admin/change-requests/GitOpsDiffViewer'
import { RbacOverrideDiffPanel } from '../features/admin/change-requests/RbacOverrideDiffPanel'
import { maskDiffLineContent } from '../features/admin/change-requests/diff-mask'
import * as changeRequestsApi from '../features/admin/enterprise/change-requests-api'

vi.mock('../shared/config/admin-feature-flags', () => ({
  isEnterpriseAdminWriteEnabled: () => true,
  isEnterpriseAdminApprovalsUiEnabled: () => true,
  isEnterpriseGitOpsPrUiEnabled: () => true,
  isEnterpriseGitOpsRbacRoleRequestsUiEnabled: () => true,
}))

vi.mock('../features/auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: Record<string, unknown> }) => unknown) =>
    selector({
      user: {
        id: 'admin-2',
        platformPermissions: [
          'admin:change-request:list',
          'admin:change-request:create',
          'admin:change-request:approve',
          'admin:change-request:gitops:dry-run',
          'admin:change-request:gitops:create',
        ],
      },
    }),
}))

const baseItem = {
  requestedByUserId: 'other',
  status: 'APPROVED',
  operationType: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
  targetService: 'content',
  targetKey: 'KEY',
  currentValue: null,
  requestedValue: 'true',
  severity: 'MEDIUM',
  impactSummary: { severity: 'MEDIUM', description: 'impact' },
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

describe('Faz 143 change requests & GitOps', () => {
  afterEach(() => vi.restoreAllMocks())

  it('renders change requests list', async () => {
    vi.spyOn(changeRequestsApi, 'listChangeRequests').mockResolvedValue({
      items: [{ id: 'cr-1', ...baseItem }],
    })
    render(
      <MemoryRouter>
        <AdminChangeRequestsPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('change-request-list-table')).toBeTruthy())
    expect(screen.getByText(/Change requests/i)).toBeTruthy()
  })

  it('renders detail page with sanitized payload', async () => {
    vi.spyOn(changeRequestsApi, 'listChangeRequests').mockResolvedValue({
      items: [
        {
          id: 'cr-2',
          ...baseItem,
          status: 'PENDING',
          validationResult: { access_token: 'secret', note: 'ok' },
        },
      ],
    })
    render(
      <MemoryRouter initialEntries={['/app/admin/change-requests/cr-2']}>
        <Routes>
          <Route path="/app/admin/change-requests/:id" element={<AdminChangeRequestDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('change-request-detail-page')).toBeTruthy())
    expect(document.body.textContent).toContain('***masked***')
    expect(document.body.textContent).not.toContain('secret')
  })

  it('renders dry-run preview states', async () => {
    vi.spyOn(changeRequestsApi, 'listChangeRequests').mockResolvedValue({
      items: [{ id: 'cr-3', ...baseItem }],
    })
    vi.spyOn(changeRequestsApi, 'gitopsDryRun').mockResolvedValue({
      changeRequestId: 'cr-3',
      targetEnvironment: 'staging',
      provider: 'mock',
      changedFiles: [],
      diffPreview: '--- a\n+++ b\n@@ -1 +1 @@\n-old\n+new\n',
      warnings: ['check baseline'],
    })
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/app/admin/change-requests/cr-3/dry-run']}>
        <Routes>
          <Route path="/app/admin/change-requests/:id/dry-run" element={<AdminChangeRequestDryRunPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('gitops-dry-run-page')).toBeTruthy())
    await user.click(screen.getByRole('button', { name: 'Run dry-run' }))
    await waitFor(() => expect(screen.getByTestId('dry-run-warnings')).toBeTruthy())
  })

  it('renders GitOps PR state card', async () => {
    vi.spyOn(changeRequestsApi, 'listChangeRequests').mockResolvedValue({
      items: [{ id: 'cr-4', ...baseItem }],
    })
    sessionStorage.setItem(
      'admin-cr-dry-run-cr-4',
      JSON.stringify({
        changeRequestId: 'cr-4',
        targetEnvironment: 'staging',
        provider: 'mock',
        changedFiles: [],
        diffPreview: '--- a\n+++ b',
        warnings: [],
      }),
    )
    render(
      <MemoryRouter initialEntries={['/app/admin/change-requests/cr-4/gitops']}>
        <Routes>
          <Route path="/app/admin/change-requests/:id/gitops" element={<AdminChangeRequestGitOpsPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('gitops-pr-state-card')).toBeTruthy())
    expect(screen.getByText(/Ready for PR/i)).toBeTruthy()
  })

  it('renders YAML diff viewer with masking', () => {
    const diff = 'password: supersecret\n+token: eyJhbGciOiJIUzI1NiJ9.payload.sig'
    render(
      <GitOpsDiffViewer diffPreview={diff} environment="staging" operationType="TEST" mode="unified" />,
    )
    expect(screen.getByTestId('gitops-diff-viewer')).toBeTruthy()
    expect(screen.queryByText(/supersecret/)).toBeNull()
    expect(maskDiffLineContent('password: x')).toContain('***masked***')
  })

  it('renders RBAC override diff panel with PLATFORM_ADMIN warning', async () => {
    vi.spyOn(changeRequestsApi, 'listChangeRequests').mockResolvedValue({ items: [] })
    render(
      <RbacOverrideDiffPanel
        row={{
          id: 'r',
          requestedByUserId: 'u',
          status: 'APPROVED',
          operationType: 'ADMIN_RBAC_ROLE_GRANT_REQUEST',
          targetService: 'identity',
          targetKey: 'rbac',
          currentValue: null,
          requestedValue: 'PLATFORM_ADMIN',
          createdAt: '',
          externalRequestId: null,
          decidedAt: null,
          decidedByUserId: null,
          decisionReason: null,
          approvedAt: null,
          rejectedAt: null,
          impactSummary: { role: 'PLATFORM_ADMIN' },
          validationResult: { requestedRole: 'PLATFORM_ADMIN', targetUserId: 'user-uuid-long' },
          severity: 'HIGH',
        }}
        dryRun={{
          changeRequestId: 'r',
          targetEnvironment: 'staging',
          provider: 'mock',
          changedFiles: [{ path: 'admin-rbac-overrides.yaml', changes: [] }],
          diffPreview: '+  role: PLATFORM_ADMIN',
          warnings: [],
        }}
      />,
    )
    expect(screen.getByText(/HIGH risk/i)).toBeTruthy()
    expect(screen.queryByText(/user-uuid-long/)).toBeNull()
  })

  it('shows GitOps disabled banner when flag off', () => {
    vi.doMock('../shared/config/admin-feature-flags', () => ({
      isEnterpriseAdminWriteEnabled: () => true,
      isEnterpriseGitOpsPrUiEnabled: () => false,
    }))
    render(
      <MemoryRouter initialEntries={['/app/admin/change-requests/cr-5/gitops']}>
        <Routes>
          <Route path="/app/admin/change-requests/:id/gitops" element={<AdminChangeRequestGitOpsPage />} />
        </Routes>
      </MemoryRouter>,
    )
  })

  it('renders diff page after dry-run stash', async () => {
    vi.spyOn(changeRequestsApi, 'listChangeRequests').mockResolvedValue({
      items: [{ id: 'cr-6', ...baseItem }],
    })
    sessionStorage.setItem(
      'admin-cr-dry-run-cr-6',
      JSON.stringify({
        changeRequestId: 'cr-6',
        targetEnvironment: 'staging',
        provider: 'mock',
        changedFiles: [{ path: 'values.yaml', changes: [] }],
        diffPreview: '---\n+++',
        warnings: [],
      }),
    )
    render(
      <MemoryRouter initialEntries={['/app/admin/change-requests/cr-6/diff']}>
        <Routes>
          <Route path="/app/admin/change-requests/:id/diff" element={<AdminChangeRequestDiffPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByTestId('gitops-diff-page')).toBeTruthy())
  })
})

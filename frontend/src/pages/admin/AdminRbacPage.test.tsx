import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, afterEach } from 'vitest'
import { AdminRbacPage } from './AdminRbacPage'
import * as rbacApi from '../../features/admin/rbac/admin-rbac-api'
import { PERM_RBAC_READ } from '../../features/admin/access/admin-permissions'

vi.mock('../../shared/config/admin-feature-flags', () => ({
  isAdminRbacUiEnabled: () => true,
  isAdminRbacRoleRequestsUiEnabled: () => false,
  isEnterpriseAdminWriteEnabled: () => true,
  isEnterpriseAdminApprovalsUiEnabled: () => true,
  isEnterpriseGitOpsPrUiEnabled: () => false,
  isNotificationAnalyticsUiEnabled: () => false,
  isNotificationDeadLetterUiEnabled: () => false,
  isNotificationRetentionUiEnabled: () => false,
  isNotificationLegalHoldUiEnabled: () => false,
}))

vi.mock('../../features/auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: Record<string, unknown> | null }) => unknown) =>
    selector({
      user: {
        id: 'u1',
        email: 'viewer@b.com',
        name: 'V',
        roles: [],
        platformPermissions: [PERM_RBAC_READ],
      },
    }),
}))

describe('AdminRbacPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  const renderPage = () => {
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    return render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <AdminRbacPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )
  }

  it('renders users from API', async () => {
    vi.spyOn(rbacApi, 'listAdminRbacUsers').mockResolvedValue({
      items: [
        {
          userId: 'u2',
          email: 'admin@b.com',
          status: 'ACTIVE',
          platformRoles: ['PLATFORM_AUDIT_VIEWER'],
          platformPermissions: ['admin:audit:read'],
          sources: [
            { type: 'SSO_GROUP', sourceName: 'notebook-audit-viewers', roles: ['PLATFORM_AUDIT_VIEWER'] },
          ],
          lastLoginAt: null,
          mfaVerifiedRecently: false,
          warnings: [],
        },
      ],
      page: 0,
      size: 25,
      totalElements: 1,
    })
    renderPage()
    await waitFor(() => expect(screen.getByText('admin@b.com')).toBeInTheDocument())
    expect(screen.getByText('PLATFORM_AUDIT_VIEWER')).toBeInTheDocument()
  })
})

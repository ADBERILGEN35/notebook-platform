import type { ReactElement } from 'react'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AdminOverviewPage } from './admin/AdminOverviewPage'
import { AdminSetupChecklistPage } from './admin/AdminSetupChecklistPage'
import { AdminSearchDiagnosticsPage } from './admin/AdminSearchDiagnosticsPage'
import { AdminIdentityOverviewPage } from './admin/AdminIdentityOverviewPage'
import { AdminSsoDiagnosticsPage } from './admin/AdminSsoDiagnosticsPage'
import { AdminScimProvisioningPage } from './admin/AdminScimProvisioningPage'
import { AdminRoleMappingDiagnosticsPage } from './admin/AdminRoleMappingDiagnosticsPage'
import { AdminBreakGlassOpsPage } from './admin/AdminBreakGlassOpsPage'
import { AuditEventTable } from '../features/admin/audit/AuditEventTable'
import { AuditEventDetailDrawer } from '../features/admin/audit/AuditEventDetailDrawer'
import type { AuditEvent } from '../features/admin/audit/types'

vi.mock('../features/auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: { platformPermissions: string[]; roles: string[] } }) => unknown) =>
    selector({
      user: {
        platformPermissions: [
          'admin:enterprise:status:read',
          'admin:audit:read',
          'admin:rbac:read',
          'admin:break-glass:read',
        ],
        roles: ['PLATFORM_ADMIN'],
      },
    }),
}))

vi.mock('../shared/config/admin-feature-flags', () => ({
  getAuditApiMode: () => 'mock',
  isAdminUiDevOpen: () => true,
  isEnterpriseAdminWriteEnabled: () => false,
  isPlatformRetentionGovernanceUiEnabled: () => false,
  isBreakGlassReviewUiEnabled: () => true,
  isBreakGlassRevocationUiEnabled: () => true,
  isBreakGlassRotationUiEnabled: () => false,
  isScimCompatibilityDiagnosticsUiEnabled: () => true,
  isAdminRbacUiEnabled: () => true,
  isAdminRbacOverridesStatusUiEnabled: () => true,
}))

vi.mock('../features/admin/enterprise/use-enterprise-status', () => ({
  useEnterpriseStatus: () => ({
    isLoading: false,
    isError: false,
    data: {
      environment: 'test',
      generatedAt: '2026-05-17T00:00:00Z',
      features: {
        sso: {
          enabled: true,
          providersConfigured: 1,
          allowedDomainsConfigured: true,
          adminGroupMappingConfigured: false,
          trustIdpMfa: false,
        },
        scim: {
          enabled: true,
          groupsEnabled: true,
          adminGroupsConfigured: true,
          tokenConfigured: true,
          providerType: 'azure',
        },
        mfa: { adminMfaMode: 'REQUIRED', acceptedMethods: ['TOTP'], identityMfaEnabled: true, webauthnEnabled: false },
        breakGlass: {
          enabled: true,
          credentialMode: 'STATIC',
          allowedModes: ['STATIC'],
          staticTokenConfigured: true,
          webauthnEnabled: false,
          webauthnCredentialCount: 0,
          offlineSignedEnabled: false,
          offlinePublicKeyConfigured: false,
          rotationRecommended: false,
          approvalMode: 'MANUAL',
          pendingReviewCount: 0,
          overdueReviewCount: 0,
          gatewayAllowed: true,
          adminWriteAllowed: false,
          sessionTtlMinutes: 15,
          maxActiveSessions: 2,
          requireReason: true,
          requireMfa: true,
        },
      },
      warnings: [
        {
          code: 'SSO_ADMIN_MAPPING_MISSING',
          message: 'Admin group mapping missing',
          severity: 'WARNING',
        },
      ],
    },
  }),
}))

vi.mock('../features/admin/enterprise/change-requests-api', () => ({
  listChangeRequests: vi.fn(async () => ({ items: [] })),
}))

vi.mock('../features/admin/enterprise/scim-diagnostics-api', () => ({
  fetchScimCompatibilityStatus: vi.fn(async () => ({
    scimProvider: {
      type: 'azure',
      deltaSyncEnabled: false,
      deltaSyncMode: 'disabled',
      bulkSupported: true,
      filteringSupported: true,
      patchSupported: true,
      nestedGroupsSupported: false,
      rateLimitAware: true,
      maxPageSize: 100,
      lastSyncStatus: 'OK',
      warnings: ['nested groups not supported'],
    },
    checkpoints: [],
    lastRun: null,
  })),
  fetchScimSyncRuns: vi.fn(async () => ({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })),
}))

vi.mock('../features/admin/rbac/admin-rbac-api', () => ({
  listAdminRbacUsers: vi.fn(async () => ({
    items: [
      {
        userId: 'u1',
        email: 'admin@example.com',
        status: 'ACTIVE',
        platformRoles: ['PLATFORM_ADMIN'],
        platformPermissions: [],
        sources: [{ type: 'SSO_GROUP', sourceName: 'admins', roles: ['PLATFORM_ADMIN'] }],
        lastLoginAt: null,
        mfaVerifiedRecently: true,
        warnings: [],
      },
    ],
    page: 0,
    size: 15,
    totalElements: 1,
  })),
  getAdminRbacOverridesStatus: vi.fn(async () => ({
    enabled: true,
    reloadEnabled: false,
    lastKnownGoodEnabled: true,
    failClosed: true,
    fileConfigured: true,
    loaded: true,
    fileBasename: 'overrides.yaml',
    loadedAt: null,
    checksum: 'sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890',
    manifestVersion: '1',
    lastReloadAttemptAt: null,
    lastReloadResult: 'OK',
    assignmentCount: 1,
    validAssignmentCount: 1,
    ignoredAssignmentCount: 0,
    warningCount: 0,
    errorCount: 0,
    warnings: [],
  })),
  formatOverrideChecksumShort: (c: string) => `sha256:${c.slice(7, 11)}…${c.slice(-4)}`,
}))

vi.mock('../features/admin/breakglass/admin-break-glass-api', () => ({
  listBreakGlassEvents: vi.fn(async () => ({ items: [], total: 0, overdueCount: 0 })),
  getBreakGlassEvent: vi.fn(),
  reviewBreakGlassEvent: vi.fn(),
  revokeBreakGlassEventToken: vi.fn(),
}))

vi.mock('../features/admin/breakglass/admin-break-glass-sessions-api', () => ({
  listBreakGlassActiveSessions: vi.fn(async () => ({ items: [], activeCount: 0 })),
  revokeBreakGlassSession: vi.fn(),
  revokeAllBreakGlassActiveSessions: vi.fn(),
}))

vi.mock('../features/admin/breakglass/BreakGlassActiveSessionsPanel', () => ({
  BreakGlassActiveSessionsPanel: () => <div data-testid="break-glass-sessions-panel">sessions panel</div>,
}))

const wrap = (ui: ReactElement) =>
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        {ui}
      </QueryClientProvider>
    </MemoryRouter>,
  )

function assertNoSecrets(container: HTMLElement) {
  const text = container.textContent ?? ''
  expect(text).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+/)
  expect(text).not.toMatch(/Bearer\s+[A-Za-z0-9._-]{20,}/)
  expect(text).not.toContain('secret-value')
}

describe('Faz 142 admin pages', () => {
  it('renders admin overview', () => {
    const { container } = wrap(<AdminOverviewPage />)
    expect(screen.getByRole('heading', { name: /admin overview/i })).toBeTruthy()
    assertNoSecrets(container)
  })

  it('renders admin setup checklist', () => {
    const { container } = wrap(<AdminSetupChecklistPage />)
    expect(screen.getByText(/SSO \(OIDC\/SAML\)/i)).toBeTruthy()
    assertNoSecrets(container)
  })

  it('renders admin search diagnostics', () => {
    const { container } = wrap(<AdminSearchDiagnosticsPage />)
    expect(screen.getByPlaceholderText(/diagnostics categories/i)).toBeTruthy()
    assertNoSecrets(container)
  })

  it('renders identity overview', () => {
    const { container } = wrap(<AdminIdentityOverviewPage />)
    expect(screen.getByText(/Identity & SSO overview/i)).toBeTruthy()
    assertNoSecrets(container)
  })

  it('renders SSO diagnostics', () => {
    const { container } = wrap(<AdminSsoDiagnosticsPage />)
    expect(screen.getByRole('heading', { name: /SSO diagnostics/i })).toBeTruthy()
    assertNoSecrets(container)
  })

  it('renders SCIM provisioning', () => {
    const { container } = wrap(<AdminScimProvisioningPage />)
    expect(screen.getByText(/SCIM provisioning/i)).toBeTruthy()
    assertNoSecrets(container)
  })

  it('renders role mapping diagnostics', () => {
    const { container } = wrap(<AdminRoleMappingDiagnosticsPage />)
    expect(screen.getByRole('heading', { name: /role mapping diagnostics/i })).toBeTruthy()
    assertNoSecrets(container)
  })

  it('renders break-glass ops', () => {
    const { container } = wrap(<AdminBreakGlassOpsPage />)
    expect(screen.getByText(/Break-glass operations/i)).toBeTruthy()
    expect(screen.getByTestId('break-glass-sessions-panel')).toBeTruthy()
    assertNoSecrets(container)
  })

  it('renders audit table and sanitized detail drawer', () => {
    const event: AuditEvent = {
      id: 'e1',
      source: 'identity',
      eventType: 'LOGIN',
      actorUserId: 'u1',
      workspaceId: null,
      aggregateType: 'user',
      aggregateId: 'u1',
      requestId: 'req-1',
      ipAddress: null,
      userAgent: null,
      metadata: { access_token: 'secret-value', note: 'ok' },
      createdAt: '2026-05-17T00:00:00Z',
    }
    const { container: tableContainer } = render(
      <AuditEventTable events={[event]} />,
    )
    expect(screen.getByTestId('audit-event-table')).toBeTruthy()

    const { container: drawerContainer } = render(
      <AuditEventDetailDrawer event={event} onClose={() => undefined} />,
    )
    expect(screen.getByTestId('audit-event-detail-drawer')).toBeTruthy()
    expect(drawerContainer.textContent).toContain('***masked***')
    expect(drawerContainer.textContent).not.toContain('secret-value')
    assertNoSecrets(tableContainer)
    assertNoSecrets(drawerContainer)
  })

})

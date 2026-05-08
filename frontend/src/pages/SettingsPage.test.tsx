import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { SettingsPage } from './SettingsPage'

const mockPrefs = vi.hoisted(() => ({
  data: [
    {
      notificationType: 'SECURITY_SESSIONS_REVOKED',
      label: 'Security sessions revoked',
      description: 'Required account security alerts.',
      channels: {
        IN_APP: { enabled: true, mandatory: true },
        EMAIL: { enabled: true, mandatory: true },
      },
    },
    {
      notificationType: 'COMMENT_ADDED',
      label: 'Comments',
      description: 'When someone comments on your notes.',
      channels: {
        IN_APP: { enabled: true, mandatory: false },
        EMAIL: { enabled: false, mandatory: false },
      },
    },
  ],
}))
const patchSpy = vi.hoisted(() => vi.fn(async () => mockPrefs.data))
const patchDeliverySpy = vi.hoisted(() =>
  vi.fn(async () => ({
    emailDigestEnabled: true,
    emailDigestFrequency: 'DAILY',
    quietHoursEnabled: true,
    quietHoursStart: '22:00',
    quietHoursEnd: '08:00',
    timezone: 'UTC',
  })),
)

vi.mock('../features/auth/auth-store', () => ({
  useAuthStore: (selector: (state: any) => any) =>
    selector({ user: { id: 'u1' }, clearSession: vi.fn(), refreshToken: 'r1' }),
}))
vi.mock('../features/auth/auth-api', () => ({
  logout: vi.fn(async () => ({})),
  revokeAll: vi.fn(async () => ({ revokedCount: 0 })),
}))
vi.mock('../features/admin/access/admin-access', () => ({
  canShowAdminNavigation: () => false,
}))
vi.mock('../shared/config/notifications-feature-flags', () => ({
  isNotificationPreferencesEnabled: () => true,
  isWorkspaceNotificationPreferencesEnabled: () => false,
  isMfaUiEnabled: () => true,
}))
vi.mock('../features/notifications/notification-preferences-api', () => ({
  getNotificationPreferences: vi.fn(async () => mockPrefs.data),
  patchNotificationPreferences: patchSpy,
  getNotificationDeliveryPreferences: vi.fn(async () => ({
    emailDigestEnabled: false,
    emailDigestFrequency: 'DAILY',
    quietHoursEnabled: false,
    quietHoursStart: '22:00',
    quietHoursEnd: '08:00',
    timezone: 'UTC',
  })),
  patchNotificationDeliveryPreferences: patchDeliverySpy,
}))
vi.mock('../features/auth/mfa-api', () => ({
  getMfaSettings: vi.fn(async () => ({
    mfaEnabled: false,
    webauthnEnabled: false,
    backupCodesEnabled: false,
    recoveryCodesRemaining: 0,
    activeCredentialCount: 0,
    mfaRequired: false,
    availableMethods: [],
  })),
  registrationOptions: vi.fn(async () => ({
    challenge: 'c',
    rpId: 'localhost',
    rpName: 'Notebook Platform',
    userId: 'dXNlcg',
    userName: 'test@example.com',
    userDisplayName: 'test@example.com',
    excludeCredentials: [],
    userVerification: 'preferred',
  })),
  registrationVerify: vi.fn(async () => ({})),
  generateRecoveryCodes: vi.fn(async () => ({ codes: ['AAAA-BBBB-CCCC'] })),
}))
vi.mock('../shared/security/webauthn-support', () => ({
  isWebAuthnSupported: () => false,
}))

describe('SettingsPage notification preferences + mfa', () => {
  beforeEach(() => patchSpy.mockClear())

  function renderPage() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <SettingsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )
  }

  it('renders preference defaults', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Notification preferences')).toBeTruthy())
    expect(await screen.findByText('Comments')).toBeTruthy()
    expect(screen.getByText('Delivery schedule')).toBeTruthy()
  })

  it('shows mandatory toggle as disabled', async () => {
    renderPage()
    const securityEmail = await screen.findByLabelText('SECURITY_SESSIONS_REVOKED-EMAIL')
    expect((securityEmail as HTMLInputElement).disabled).toBe(true)
  })

  it('save calls PATCH for changed preference', async () => {
    renderPage()
    const commentsEmail = await screen.findByLabelText('COMMENT_ADDED-EMAIL')
    fireEvent.click(commentsEmail)
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(patchSpy).toHaveBeenCalledTimes(1))
  })

  it('saves delivery preferences', async () => {
    renderPage()
    const digestToggle = await screen.findByLabelText('delivery-email-digest-enabled')
    fireEvent.click(digestToggle)
    fireEvent.click(screen.getByRole('button', { name: 'Save delivery schedule' }))
    await waitFor(() => expect(patchDeliverySpy).toHaveBeenCalledTimes(1))
  })

  it('shows error state when save fails', async () => {
    patchSpy.mockImplementationOnce(async () => {
      throw new Error('save failed')
    })
    renderPage()
    const commentsEmail = await screen.findByLabelText('COMMENT_ADDED-EMAIL')
    fireEvent.click(commentsEmail)
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(screen.getByText(/unexpected error/i)).toBeTruthy())
  })

  it('renders MFA section with unsupported browser message', async () => {
    renderPage()
    expect(await screen.findByText('Multi-factor authentication')).toBeTruthy()
    expect(screen.getByText(/not supported in this browser/i)).toBeTruthy()
  })
})

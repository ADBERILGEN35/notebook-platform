import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { SettingsPage } from './SettingsPage'

type MockAuthState = {
  user: { id: string }
  clearSession: () => void
  refreshToken: string
}

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
  useAuthStore: <T,>(selector: (state: MockAuthState) => T) =>
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
  isWorkspaceNotificationPoliciesEnabled: () => false,
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
vi.mock('../shared/config/offline-feature-flags', () => ({
  isOfflineNotesEnabled: () => true,
  isOfflineEditEnabled: () => true,
  isOfflineSyncEnabled: () => false,
  isOfflineBackgroundSyncEnabled: () => false,
  isSwBackgroundSyncEnabled: () => true,
  isSwBackgroundSyncDryRunOnly: () => true,
  isSwBackgroundSyncRegisterEnabled: () => false,
  offlineBackgroundSyncMode: () => 'disabled',
  isOfflineEncryptionEnabled: () => true,
  isOfflineDraftEncryptionRequired: () => true,
  isOfflineCacheEncryptionEnabled: () => false,
  offlineSyncRolloutMode: () => 'disabled',
}))
vi.mock('../features/offline/offline-crypto', () => ({
  isOfflineCryptoSupported: () => true,
  hasOfflineEncryptionKey: () => false,
}))
vi.mock('../features/offline/offline-note-cache', () => ({
  listOfflineNotes: vi.fn(async () => []),
  clearOfflineNotes: vi.fn(async () => undefined),
}))
vi.mock('../features/offline/offline-note-drafts', () => ({
  listPendingDrafts: vi.fn(async () => [{ noteId: 'n1' }]),
  listOfflineDraftOverview: vi.fn(async () => []),
  listOfflineDrafts: vi.fn(async () => [
    {
      draftId: 'd1',
      noteId: 'n1',
      workspaceId: 'ws',
      notebookId: 'nb',
      baseEtag: 'e1',
      baseUpdatedAt: new Date().toISOString(),
      baseSnapshot: { title: 'Server title', contentBlocks: [] },
      localSnapshot: { title: 'Offline title', contentBlocks: [] },
      status: 'DRAFT',
      lastEditedAt: new Date().toISOString(),
      queuedAt: null,
      syncedAt: null,
      conflictReason: null,
      attemptCount: 0,
      lastError: null,
    },
  ]),
  deleteOfflineDraft: vi.fn(async () => undefined),
}))
vi.mock('../features/offline/offline-sync-service', () => ({
  syncOfflineDraft: vi.fn(async () => ({ status: 'queued', noteId: 'n1', reason: 'DISABLED' })),
  syncPendingDrafts: vi.fn(async () => []),
  refreshOfflineDraftDiagnostics: vi.fn(async () => undefined),
}))
vi.mock('../features/offline/offline-sync-diagnostics', () => ({
  getOfflineSyncDiagnostics: () => ({
    draftsPending: 0,
    draftsConflict: 0,
    draftsFailed: 0,
    lastSyncAttemptAt: null,
    syncAttempts: 0,
    syncSuccess: 0,
    lastBackgroundSyncStartedAt: null,
    lastBackgroundSyncCompletedAt: null,
    lastBackgroundSyncMode: null,
    backgroundAttempted: 0,
    backgroundSynced: 0,
    backgroundConflicts: 0,
    backgroundFailed: 0,
    backgroundQueued: 0,
    backgroundSkipped: 0,
    backgroundSkippedReasons: null,
    backgroundStoppedReason: null,
    lastBackgroundSyncResult: null,
  }),
}))
vi.mock('../features/offline/offline-background-sync-service', () => ({
  runForegroundBackgroundSync: vi.fn(async () => ({
    startedAt: new Date().toISOString(),
    completedAt: new Date().toISOString(),
    attempted: 0,
    synced: 0,
    conflicts: 0,
    failed: 0,
    queued: 0,
    skipped: 0,
    needsUserConsent: false,
    mode: 'disabled',
    stopReason: 'DISABLED',
    eligibleCount: 0,
    skippedReasons: {
      conflict: 0,
      failed: 0,
      locked: 0,
      missing_base_etag: 0,
      max_attempts: 0,
      stale_or_too_old: 0,
      session_unavailable: 0,
      network_guardrail: 0,
      encryption_key_unavailable: 0,
      requires_user_review: 0,
      currently_editing: 0,
      syncing: 0,
      synced: 0,
      status_not_eligible: 0,
      batch_limit: 0,
    },
  })),
}))
vi.mock('../features/offline/offline-sync-preferences', () => ({
  getBackgroundSyncModePreference: () => null,
  setBackgroundSyncModePreference: vi.fn(),
}))
vi.mock('../features/offline/sw-background-sync-diagnostics', () => ({
  getSwBackgroundSyncSummary: vi.fn(async () => ({
    id: 'latest',
    startedAt: new Date().toISOString(),
    completedAt: new Date().toISOString(),
    mode: 'dry-run',
    eligible: 0,
    skipped: 1,
    skipReasons: {
      conflict: 0,
      failed: 0,
      locked: 0,
      encrypted_key_unavailable: 1,
      missing_base_etag: 0,
      max_attempts: 0,
      session_unavailable: 0,
      csrf_unavailable: 0,
      currently_editing_unknown: 0,
      browser_unsupported: 0,
      status_not_eligible: 0,
      batch_limit: 0,
      network_unavailable: 0,
    },
    supported: false,
    registered: false,
    dryRunOnly: true,
    stopReason: null,
  })),
}))
vi.mock('../features/offline/sw-background-sync-policy', () => ({
  getSwBackgroundSyncSupport: () => ({
    serviceWorkerSupported: true,
    syncManagerSupported: false,
    supported: false,
    reason: 'sync_manager_unsupported',
  }),
}))
vi.mock('../features/offline/sw-background-sync-registration', () => ({
  registerSwBackgroundSync: vi.fn(async () => ({
    supported: false,
    registered: false,
    reason: 'register_disabled',
    tags: [],
  })),
  runSwBackgroundSyncDryRun: vi.fn(async () => ({
    id: 'latest',
    startedAt: new Date().toISOString(),
    completedAt: new Date().toISOString(),
    mode: 'dry-run',
    eligible: 1,
    skipped: 0,
    skipReasons: {
      conflict: 0,
      failed: 0,
      locked: 0,
      encrypted_key_unavailable: 0,
      missing_base_etag: 0,
      max_attempts: 0,
      session_unavailable: 0,
      csrf_unavailable: 0,
      currently_editing_unknown: 0,
      browser_unsupported: 0,
      status_not_eligible: 0,
      batch_limit: 0,
      network_unavailable: 0,
    },
    supported: false,
    registered: false,
    dryRunOnly: true,
    stopReason: null,
  })),
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

  it('renders offline drafts list and disabled sync in environment', async () => {
    renderPage()
    expect(await screen.findByTestId('offline-drafts-list')).toBeTruthy()
    expect(screen.getByText(/^Offline drafts$/)).toBeTruthy()
    expect(screen.getByText(/sync is disabled in this environment/i)).toBeTruthy()
    expect(screen.getByText(/Service Worker Background Sync: Unsupported/i)).toBeTruthy()
  })
})

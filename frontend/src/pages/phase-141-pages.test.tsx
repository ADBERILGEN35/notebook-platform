import type { ReactElement } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { GlobalSearchOverlay } from '../features/search/components/GlobalSearchOverlay'
import { SearchResultsPage } from './SearchResultsPage'
import { SearchDiscoveryPage } from './SearchDiscoveryPage'
import { UserSettingsPage } from './UserSettingsPage'
import { AccountSecurityPage } from './AccountSecurityPage'
import { NotificationPreferencesPage } from './NotificationPreferencesPage'
import { OfflineSyncDiagnosticsPage } from './OfflineSyncDiagnosticsPage'

vi.mock('../features/search/search-api', () => ({
  searchNotes: vi.fn(async () => ({
    items: [{ noteId: 'n1', workspaceId: 'w1', title: 'Doc', snippet: 'hello', rank: 1 }],
    page: 0,
    size: 8,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  })),
}))

vi.mock('../features/workspaces/workspace-api', () => ({
  listWorkspaces: vi.fn(async () => ({
    items: [{ id: 'w1', name: 'Alpha', slug: 'a', type: 'TEAM', ownerId: 'u1', createdAt: '', updatedAt: '' }],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  })),
}))

vi.mock('../features/workspaces/workspace-store', () => ({
  useWorkspaceStore: (selector: (s: { activeWorkspaceId: string }) => unknown) => selector({ activeWorkspaceId: 'w1' }),
}))

vi.mock('../features/auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: { id: string; name: string; email: string }; refreshToken: string; clearSession: () => void }) => unknown) =>
    selector({ user: { id: 'u1', name: 'Ada', email: 'a@b.com' }, refreshToken: 'r1', clearSession: vi.fn() }),
}))

vi.mock('../shared/config/notifications-feature-flags', () => ({
  isNotificationPreferencesEnabled: () => true,
  isMfaUiEnabled: () => true,
}))

vi.mock('../features/notifications/notification-preferences-api', () => ({
  getNotificationPreferences: vi.fn(async () => [
    {
      notificationType: 'COMMENT_ADDED',
      label: 'Comments',
      description: 'Note comments',
      channels: {
        IN_APP: { enabled: true, mandatory: false },
        EMAIL: { enabled: false, mandatory: false },
      },
    },
  ]),
  patchNotificationPreferences: vi.fn(),
}))

vi.mock('../features/auth/mfa-api', () => ({
  getMfaSettings: vi.fn(async () => ({
    mfaEnabled: true,
    webauthnEnabled: false,
    recoveryCodesRemaining: 0,
    activeCredentialCount: 0,
  })),
}))

vi.mock('../features/auth/auth-api', () => ({
  logout: vi.fn(),
  revokeAll: vi.fn(async () => ({ revokedCount: 0 })),
}))

vi.mock('../shared/config/offline-feature-flags', () => ({
  isOfflineNotesEnabled: () => true,
  isOfflineEditEnabled: () => true,
  isOfflineSyncEnabled: () => false,
  isOfflineBackgroundSyncEnabled: () => false,
  offlineBackgroundSyncMode: () => 'disabled',
}))

vi.mock('../features/offline/offline-sync-diagnostics', () => ({
  getOfflineSyncDiagnostics: vi.fn(async () => ({
    draftsPending: 0,
    draftsConflict: 0,
    draftsFailed: 0,
  })),
}))

vi.mock('../features/offline/offline-note-cache', () => ({
  listOfflineNotes: vi.fn(async () => []),
  clearOfflineNotes: vi.fn(),
}))

vi.mock('../features/offline/offline-note-drafts', () => ({
  listPendingDrafts: vi.fn(async () => []),
  listOfflineDraftOverview: vi.fn(async () => []),
}))

vi.mock('../features/offline/offline-crypto', () => ({
  hasOfflineEncryptionKey: () => false,
}))

vi.mock('../shared/hooks/useOnlineStatus', () => ({
  useOnlineStatus: () => ({ isOnline: true }),
}))

const wrap = (ui: ReactElement, path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <Routes>
          <Route path="/app/search" element={ui} />
          <Route path="/app/search/discover" element={ui} />
          <Route path="/app/settings" element={ui} />
          <Route path="/app/settings/security" element={ui} />
          <Route path="/app/settings/notifications" element={ui} />
          <Route path="/app/settings/sync" element={ui} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  )

describe('Faz 141', () => {
  it('GlobalSearchOverlay opens and shows permission note', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <GlobalSearchOverlay open onClose={vi.fn()} />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    expect(screen.getByRole('dialog', { name: /global search/i })).toBeTruthy()
    expect(screen.getByText(/permission-filtered/i)).toBeTruthy()
  })

  it('SearchResultsPage reads q param', async () => {
    wrap(<SearchResultsPage />, '/app/search?q=hello')
    expect(await screen.findByDisplayValue('hello')).toBeTruthy()
  })

  it('SearchDiscoveryPage renders', async () => {
    wrap(<SearchDiscoveryPage />, '/app/search/discover')
    expect(await screen.findByText('Search & discovery')).toBeTruthy()
  })

  it('UserSettingsPage renders without tokens', () => {
    const { container } = wrap(<UserSettingsPage />, '/app/settings')
    expect(screen.getByText('Profile')).toBeTruthy()
    const text = container.textContent ?? ''
    expect(text).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/)
    expect(text).not.toMatch(/Bearer\s+/i)
  })

  it('AccountSecurityPage renders sessions shell', async () => {
    wrap(<AccountSecurityPage />, '/app/settings/security')
    expect(await screen.findByText('Security & sessions')).toBeTruthy()
    expect(screen.getByText(/never shown here/i)).toBeTruthy()
  })

  it('NotificationPreferencesPage renders', async () => {
    wrap(<NotificationPreferencesPage />, '/app/settings/notifications')
    expect(await screen.findByText('Notification preferences')).toBeTruthy()
    expect(await screen.findByText('Comments')).toBeTruthy()
  })

  it('OfflineSyncDiagnosticsPage renders', async () => {
    wrap(<OfflineSyncDiagnosticsPage />, '/app/settings/sync')
    expect(await screen.findByText('Offline & sync')).toBeTruthy()
  })

  it('GlobalSearchOverlay closes on Escape', () => {
    const onClose = vi.fn()
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <GlobalSearchOverlay open onClose={onClose} />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onClose).toHaveBeenCalled()
  })
})

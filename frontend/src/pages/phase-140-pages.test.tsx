import type { ReactElement } from 'react'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { WorkspaceMembersPage } from './WorkspaceMembersPage'
import { WorkspaceSettingsPage } from './WorkspaceSettingsPage'
import { NotificationCenterPage } from './NotificationCenterPage'
import { NoteEditorPage } from './NoteEditorPage'
import { AccessDeniedState } from '../features/access/components/AccessDeniedState'
import { InviteMemberModal } from '../features/collaboration/components/InviteMemberModal'

vi.mock('../features/workspace-members/workspace-members-api', () => ({
  listWorkspaceMembers: vi.fn(async () => ({
    items: [{ workspaceId: 'w1', userId: 'u1', role: 'OWNER', joinedAt: '2026-01-01', createdAt: '2026-01-01', updatedAt: '2026-01-01' }],
    page: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  })),
  listWorkspaceInvitations: vi.fn(async () => ({
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  })),
}))

vi.mock('../features/workspaces/workspace-api', () => ({
  getWorkspace: vi.fn(async () => ({
    id: 'w1',
    name: 'Alpha',
    slug: 'alpha',
    type: 'TEAM',
    ownerId: 'u1',
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01',
  })),
  updateWorkspace: vi.fn(),
}))

vi.mock('../features/notes/note-api', () => ({
  getNote: vi.fn(async () => ({
    note: {
      id: 'n1',
      workspaceId: 'w1',
      notebookId: 'nb1',
      title: 'Spec',
      contentBlocks: [],
      contentSchemaVersion: 1,
      createdAt: '2026-01-01',
      updatedAt: '2026-01-01',
    },
    etag: 'v1',
  })),
}))

vi.mock('./NotePage', () => ({
  NotePage: () => <div data-testid="note-page-embedded">Editor</div>,
}))

vi.mock('../shared/config/notifications-feature-flags', () => ({
  isNotificationsEnabled: () => true,
}))

vi.mock('../features/notifications/notification-hooks', () => ({
  useNotifications: () => ({ isLoading: false, isError: false, data: { items: [], hasNext: false, hasPrevious: false } }),
  useUnreadNotificationCount: () => ({ data: 0 }),
  useMarkNotificationRead: () => ({ mutate: vi.fn() }),
  useMarkAllNotificationsRead: () => ({ mutate: vi.fn(), isPending: false }),
  useArchiveNotification: () => ({ mutate: vi.fn() }),
}))

const wrap = (ui: ReactElement, path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <Routes>
          <Route path="/app/workspaces/:workspaceId/members" element={ui} />
          <Route path="/app/workspaces/:workspaceId/settings" element={ui} />
          <Route path="/app/workspaces/:workspaceId/notes/:noteId" element={ui} />
          <Route path="/app/notifications" element={ui} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  )

describe('Faz 140 pages', () => {
  it('WorkspaceMembersPage renders member list', async () => {
    wrap(<WorkspaceMembersPage />, '/app/workspaces/w1/members')
    expect(await screen.findByText('Members & access')).toBeTruthy()
    expect(await screen.findByText('Owner')).toBeTruthy()
  })

  it('WorkspaceSettingsPage renders sections', async () => {
    wrap(<WorkspaceSettingsPage />, '/app/workspaces/w1/settings')
    expect(await screen.findByText('Workspace settings')).toBeTruthy()
    expect(await screen.findByText('General')).toBeTruthy()
    expect(await screen.findByText('Danger zone')).toBeTruthy()
  })

  it('NotificationCenterPage renders inbox', async () => {
    wrap(<NotificationCenterPage />, '/app/notifications')
    expect(await screen.findByText('Notification center')).toBeTruthy()
    expect(screen.getByText('All caught up')).toBeTruthy()
  })

  it('NoteEditorPage renders shell and embedded editor', async () => {
    wrap(<NoteEditorPage />, '/app/workspaces/w1/notes/n1')
    expect(await screen.findByText('Share & collaborate')).toBeTruthy()
    expect(screen.getByTestId('note-page-embedded')).toBeTruthy()
  })

  it('AccessDeniedState sanitizes token-like message text', () => {
    const { container } = render(
      <AccessDeniedState message="Access denied for eyJhbGciOiJIUzI1NiJ9.test" />,
    )
    const text = container.textContent ?? ''
    expect(text).toContain('[redacted]')
    expect(text).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/)
  })

  it('InviteMemberModal renders', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <InviteMemberModal open workspaceId="w1" onClose={vi.fn()} />
      </QueryClientProvider>,
    )
    expect(screen.getByText('Invite member')).toBeTruthy()
    expect(screen.getByText('Send invitation')).toBeTruthy()
  })
})

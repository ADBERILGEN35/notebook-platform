import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SideNav } from './SideNav'
import { TopNav } from './TopNav'

vi.mock('./UserMenu', () => ({
  UserMenu: () => <span data-testid="user-menu">User</span>,
}))

vi.mock('../../notifications/components/NotificationBell', () => ({
  NotificationBell: () => <span data-testid="notification-bell">Bell</span>,
}))

vi.mock('../../../shared/hooks/useMediaQuery', () => ({
  useMediaQuery: (query: string) => query === '(min-width: 1024px)',
}))

vi.mock('../../auth/auth-store', () => ({
  useAuthStore: () => ({ user: { name: 'Test', email: 't@e.com' } }),
}))

const workspaces = [
  {
    id: 'w1',
    name: 'Alpha',
    slug: 'alpha',
    type: 'TEAM' as const,
    ownerId: 'u1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

const wrap = (ui: React.ReactNode, initialEntries = ['/app/workspaces']) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )

describe('Faz 151C AppShell visual alignment', () => {
  it('SideNav brand mark shows NP rosette and Enterprise Workspace subtitle', () => {
    wrap(
      <SideNav
        workspaces={workspaces}
        notebooks={[]}
        activeWorkspaceId="w1"
        onWorkspaceSelect={vi.fn()}
        onCreateNotebook={vi.fn()}
        onSignOut={vi.fn()}
      />,
    )
    expect(screen.getByText('NP')).toBeTruthy()
    expect(screen.getByText('Notebook Platform')).toBeTruthy()
    expect(screen.getByText(/enterprise workspace/i)).toBeTruthy()
  })

  it('SideNav New Notebook CTA fires callback and is disabled without workspace', () => {
    const onCreate = vi.fn()
    const { unmount } = wrap(
      <SideNav
        workspaces={workspaces}
        notebooks={[]}
        activeWorkspaceId="w1"
        onWorkspaceSelect={vi.fn()}
        onCreateNotebook={onCreate}
        onSignOut={vi.fn()}
      />,
    )
    fireEvent.click(screen.getByTestId('sidenav-new-notebook'))
    expect(onCreate).toHaveBeenCalled()
    unmount()

    wrap(
      <SideNav
        workspaces={[]}
        notebooks={[]}
        activeWorkspaceId={null}
        onWorkspaceSelect={vi.fn()}
        onCreateNotebook={vi.fn()}
        onSignOut={vi.fn()}
      />,
    )
    const button = screen.getByTestId('sidenav-new-notebook') as HTMLButtonElement
    expect(button.disabled).toBe(true)
  })

  it('SideNav Sign Out fires callback', () => {
    const onSignOut = vi.fn()
    wrap(
      <SideNav
        workspaces={workspaces}
        notebooks={[]}
        activeWorkspaceId="w1"
        onWorkspaceSelect={vi.fn()}
        onCreateNotebook={vi.fn()}
        onSignOut={onSignOut}
      />,
    )
    fireEvent.click(screen.getByTestId('sidenav-sign-out'))
    expect(onSignOut).toHaveBeenCalled()
  })

  it('SideNav renders compact navigation links (Workspaces, Search, Notifications, Settings)', () => {
    wrap(
      <SideNav
        workspaces={workspaces}
        notebooks={[]}
        activeWorkspaceId="w1"
        onWorkspaceSelect={vi.fn()}
        onCreateNotebook={vi.fn()}
        onSignOut={vi.fn()}
        showAdminNav
      />,
    )
    expect(screen.getByText('Workspaces')).toBeTruthy()
    expect(screen.getByRole('link', { name: /^search$/i })).toBeTruthy()
    expect(screen.getByRole('link', { name: /^notifications$/i })).toBeTruthy()
    expect(screen.getAllByRole('link', { name: /settings/i }).length).toBeGreaterThan(0)
    expect(screen.getByRole('link', { name: /^admin$/i })).toBeTruthy()
    expect(screen.getByRole('link', { name: /^support$/i })).toBeTruthy()
  })

  it('TopNav renders rounded search pill and Ctrl+K hint', () => {
    wrap(
      <TopNav
        search=""
        onSearchChange={vi.fn()}
        onCreateNote={vi.fn()}
        workspaces={workspaces}
        activeWorkspaceId="w1"
        onWorkspaceSelect={vi.fn()}
      />,
    )
    expect(screen.getByPlaceholderText(/search workspace/i)).toBeTruthy()
    expect(screen.getByText(/ctrl\+k/i)).toBeTruthy()
  })

  it('TopNav does not leak tokens or secrets in markup', () => {
    const { container } = wrap(
      <TopNav
        search="alpha"
        onSearchChange={vi.fn()}
        onCreateNote={vi.fn()}
        workspaces={workspaces}
        activeWorkspaceId="w1"
        onWorkspaceSelect={vi.fn()}
        onInviteTeam={vi.fn()}
      />,
    )
    const text = container.textContent ?? ''
    expect(text).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/)
    expect(text).not.toMatch(/Bearer\s+[A-Za-z0-9._-]+/)
  })
})

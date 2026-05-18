import type { ReactNode } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AppShell } from './AppShell'
import { SideNav } from './SideNav'
import { TopNav } from './TopNav'

vi.mock('./UserMenu', () => ({
  UserMenu: () => <span data-testid="user-menu">User</span>,
}))

vi.mock('../../notifications/components/NotificationBell', () => ({
  NotificationBell: () => <span data-testid="notification-bell">Bell</span>,
}))

vi.mock('../../../shared/hooks/useMediaQuery', () => ({
  useMediaQuery: () => false,
}))

vi.mock('../../auth/auth-store', () => ({
  useAuthStore: (selector: (state: { user: { name: string; email: string }; logout: () => void }) => unknown) =>
    selector({
      user: { name: 'Ada Lovelace', email: 'ada@example.com' },
      logout: vi.fn(),
    }),
}))

const withProviders = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )

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
const notebooks = [
  {
    id: 'n1',
    name: 'Notes',
    workspaceId: 'w1',
    createdBy: 'u1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

describe('AppShell layout', () => {
  it('renders AppShell with main landmark and child content', () => {
    withProviders(
        <AppShell
          isDesktop
          mobileNavOpen={false}
          onMobileNavOpen={vi.fn()}
          onMobileNavClose={vi.fn()}
          nav={{
            workspaces,
            notebooks,
            activeWorkspaceId: 'w1',
            onWorkspaceSelect: vi.fn(),
          }}
          top={{
            search: '',
            onSearchChange: vi.fn(),
            onCreateNote: vi.fn(),
            workspaces,
            activeWorkspaceId: 'w1',
            onWorkspaceSelect: vi.fn(),
          }}
        >
          <p>Hub content</p>
        </AppShell>,
    )
    expect(screen.getByRole('main')).toBeTruthy()
    expect(screen.getByText('Hub content')).toBeTruthy()
  })

  it('renders SideNav with workspace and hub links', () => {
    render(
      <MemoryRouter>
        <SideNav
          workspaces={workspaces}
          notebooks={notebooks}
          activeWorkspaceId="w1"
          onWorkspaceSelect={vi.fn()}
        />
      </MemoryRouter>,
    )
    expect(screen.getByRole('navigation', { name: /workspace navigation/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Alpha' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Hub' })).toBeTruthy()
  })

  it('renders TopNav with search and sidebar toggle', () => {
    const onSidebarToggle = vi.fn()
    withProviders(
        <TopNav
          search=""
          onSearchChange={vi.fn()}
          onCreateNote={vi.fn()}
          onSidebarToggle={onSidebarToggle}
          workspaces={workspaces}
          activeWorkspaceId="w1"
          onWorkspaceSelect={vi.fn()}
        />,
    )
    fireEvent.click(screen.getByTestId('sidebar-toggle'))
    expect(onSidebarToggle).toHaveBeenCalled()
    expect(screen.getByLabelText(/search notes/i)).toBeTruthy()
  })
})

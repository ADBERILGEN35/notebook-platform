import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { WorkspaceDashboardEmpty } from './components/WorkspaceDashboardEmpty'
import { WorkspaceDashboardPopulated } from './components/WorkspaceDashboardPopulated'
import { WorkspaceQuickActions } from './components/WorkspaceQuickActions'
import { formatRelativeTime } from './utils/format-relative-time'

vi.mock('../../shared/hooks/useOnlineStatus', () => ({
  useOnlineStatus: () => ({ isOnline: true }),
}))

describe('Faz 151A workspace dashboard', () => {
  it('renders empty dashboard with h1 welcome and CTA', () => {
    render(
      <MemoryRouter>
        <WorkspaceDashboardEmpty
          workspaceName=""
          onWorkspaceNameChange={vi.fn()}
          onCreateWorkspace={vi.fn()}
          createPending={false}
          createError={false}
          error={null}
          showGuidedSetup
          onGuidedSetup={vi.fn()}
          onSearch={vi.fn()}
          onNotifications={vi.fn()}
        />
      </MemoryRouter>,
    )
    expect(screen.getByTestId('workspace-dashboard-empty')).toBeTruthy()
    expect(screen.getByRole('heading', { level: 1, name: /welcome to your workspace/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /create workspace/i })).toBeTruthy()
    expect(screen.getByText(/built for teams/i)).toBeTruthy()
  })

  it('renders populated dashboard with workspace cards and quick actions', () => {
    render(
      <MemoryRouter>
        <WorkspaceDashboardPopulated
          userName="Ada Lovelace"
          workspaces={[
            {
              id: 'w1',
              name: 'Engineering',
              slug: 'engineering',
              type: 'TEAM',
              ownerId: 'u1',
              createdAt: '2026-01-01T00:00:00Z',
              updatedAt: '2026-01-01T00:00:00Z',
            },
          ]}
          notebooks={[]}
          focusWorkspaceId="w1"
          notebooksLoading={false}
          newWorkspaceName=""
          onWorkspaceNameChange={vi.fn()}
          onCreateWorkspace={vi.fn()}
          createPending={false}
          createError={false}
          error={null}
          onSearch={vi.fn()}
          onNotifications={vi.fn()}
          onNewNotebook={vi.fn()}
          onManageWorkspace={vi.fn()}
        />
      </MemoryRouter>,
    )
    expect(screen.getByTestId('workspace-dashboard-populated')).toBeTruthy()
    expect(screen.getByText(/welcome back, ada/i)).toBeTruthy()
    expect(screen.getByRole('link', { name: /engineering/i })).toBeTruthy()
    expect(screen.getByText('Quick actions')).toBeTruthy()
    expect(screen.getByText('At a glance')).toBeTruthy()
  })

  it('renders quick actions with accessible labels', () => {
    render(
      <WorkspaceQuickActions
        focusWorkspaceId="w1"
        onSearch={vi.fn()}
        onNotifications={vi.fn()}
        onOpenWorkspace={vi.fn()}
      />,
    )
    expect(screen.getByRole('button', { name: /quick note/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /notifications/i })).toBeTruthy()
  })

  it('does not expose tokens in dashboard markup', () => {
    const { container } = render(
      <MemoryRouter>
        <WorkspaceDashboardPopulated
          workspaces={[
            {
              id: 'w1',
              name: 'Team',
              slug: 'team',
              type: 'TEAM',
              ownerId: 'u1',
              createdAt: '2026-01-01T00:00:00Z',
              updatedAt: '2026-01-01T00:00:00Z',
            },
          ]}
          notebooks={[]}
          focusWorkspaceId="w1"
          notebooksLoading={false}
          newWorkspaceName=""
          onWorkspaceNameChange={vi.fn()}
          onCreateWorkspace={vi.fn()}
          createPending={false}
          createError={false}
          error={null}
          onSearch={vi.fn()}
          onNotifications={vi.fn()}
          onNewNotebook={vi.fn()}
          onManageWorkspace={vi.fn()}
        />
      </MemoryRouter>,
    )
    const text = container.textContent ?? ''
    expect(text).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/)
    expect(text).not.toMatch(/Bearer\s+[A-Za-z0-9._-]+/)
  })

  it('formats relative time for recent work', () => {
    const recent = new Date(Date.now() - 5 * 60_000).toISOString()
    expect(formatRelativeTime(recent)).toMatch(/min ago/)
  })
})

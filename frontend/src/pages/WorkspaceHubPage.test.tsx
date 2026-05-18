import type { ReactElement } from 'react'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { WorkspaceHubPage } from './WorkspaceHubPage'

const workspaceFixture = {
  items: [
    {
      id: 'w1',
      name: 'Alpha Team',
      slug: 'alpha-team',
      type: 'TEAM' as const,
      ownerId: 'u1',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
}

const emptyWorkspacePage = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
  hasPrevious: false,
}

vi.mock('../features/workspaces/workspace-api', () => ({
  listWorkspaces: vi.fn(),
  createWorkspace: vi.fn(),
}))

vi.mock('../features/notebooks/notebook-api', () => ({
  listNotebooks: vi.fn(async () => ({
    items: [],
    page: 0,
    size: 6,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  })),
}))

vi.mock('../features/workspaces/workspace-store', () => ({
  useWorkspaceStore: (selector: (state: { activeWorkspaceId: string | null; setActiveWorkspaceId: () => void }) => unknown) =>
    selector({ activeWorkspaceId: 'w1', setActiveWorkspaceId: vi.fn() }),
}))

const wrap = (ui: ReactElement, path = '/app/workspaces') =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        {ui}
      </QueryClientProvider>
    </MemoryRouter>,
  )

describe('WorkspaceHubPage', () => {
  it('renders empty state when no workspaces', async () => {
    const { listWorkspaces } = await import('../features/workspaces/workspace-api')
    vi.mocked(listWorkspaces).mockResolvedValue(emptyWorkspacePage)
    wrap(<WorkspaceHubPage />)
    expect(await screen.findByRole('region', { name: /no workspaces yet/i })).toBeTruthy()
    expect(screen.getByLabelText('New workspace name')).toBeTruthy()
  })

  it('renders populated hub with workspace cards (test fixture)', async () => {
    const { listWorkspaces } = await import('../features/workspaces/workspace-api')
    vi.mocked(listWorkspaces).mockResolvedValue(workspaceFixture)
    wrap(<WorkspaceHubPage />)
    expect(await screen.findByText('Workspace hub')).toBeTruthy()
    expect(screen.getByRole('link', { name: /alpha team/i })).toBeTruthy()
    expect(screen.getByText('Quick actions')).toBeTruthy()
  })

  it('does not expose raw tokens in hub markup', async () => {
    const { listWorkspaces } = await import('../features/workspaces/workspace-api')
    vi.mocked(listWorkspaces).mockResolvedValue(workspaceFixture)
    const { container } = wrap(<WorkspaceHubPage />)
    await screen.findByText('Workspace hub')
    const text = container.textContent ?? ''
    expect(text).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/)
    expect(text).not.toMatch(/Bearer\s+[A-Za-z0-9._-]+/)
  })
})

import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { WorkspaceDashboardEmpty } from './components/WorkspaceDashboardEmpty'
import { WorkspaceDashboardPopulated } from './components/WorkspaceDashboardPopulated'

vi.mock('../../shared/hooks/useOnlineStatus', () => ({
  useOnlineStatus: () => ({ isOnline: true }),
}))

const renderEmpty = (overrides: Partial<Parameters<typeof WorkspaceDashboardEmpty>[0]> = {}) =>
  render(
    <MemoryRouter>
      <WorkspaceDashboardEmpty
        workspaceName=""
        onWorkspaceNameChange={vi.fn()}
        onCreateWorkspace={vi.fn()}
        createPending={false}
        createError={false}
        error={null}
        showOnboarding={false}
        onDismissOnboarding={vi.fn()}
        onFocusCreate={vi.fn()}
        onQuickNote={vi.fn()}
        onInviteMember={vi.fn()}
        {...overrides}
      />
    </MemoryRouter>,
  )

const baseWorkspace = {
  id: 'w1',
  name: 'Engineering',
  slug: 'engineering',
  type: 'TEAM' as const,
  ownerId: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const renderPopulated = (
  overrides: Partial<Parameters<typeof WorkspaceDashboardPopulated>[0]> = {},
) =>
  render(
    <MemoryRouter>
      <WorkspaceDashboardPopulated
        userName="Ada"
        workspaces={[baseWorkspace]}
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
        {...overrides}
      />
    </MemoryRouter>,
  )

describe('Faz 152C interaction audit — classifications', () => {
  describe('Empty dashboard', () => {
    it('Create New Workspace CTA is enabled with a name and triggers callback', () => {
      const onCreate = vi.fn()
      renderEmpty({ workspaceName: 'Team Alpha', onCreateWorkspace: onCreate })
      const cta = screen.getByRole('button', { name: /create new workspace/i }) as HTMLButtonElement
      expect(cta.disabled).toBe(false)
      fireEvent.click(cta)
      expect(onCreate).toHaveBeenCalled()
    })

    it('Create New Workspace CTA is disabled without a name (DISABLED_BY_DESIGN)', () => {
      renderEmpty({ workspaceName: '' })
      const cta = screen.getByRole('button', { name: /create new workspace/i }) as HTMLButtonElement
      expect(cta.disabled).toBe(true)
    })

    it('Quick Note button is disabled when no active workspace (DISABLED_BY_DESIGN)', () => {
      renderEmpty()
      const button = screen.getByRole('button', { name: /quick note/i }) as HTMLButtonElement
      expect(button.disabled).toBe(true)
    })

    it('Invite Member button is disabled when no active workspace (DISABLED_BY_DESIGN)', () => {
      renderEmpty()
      const button = screen.getByRole('button', { name: /invite member/i }) as HTMLButtonElement
      expect(button.disabled).toBe(true)
    })

    it('Browse Discovery link points to /app/search/discover (WORKING)', () => {
      renderEmpty()
      const link = screen.getByRole('link', { name: /browse discovery/i }) as HTMLAnchorElement
      expect(link.getAttribute('href')).toBe('/app/search/discover')
    })
  })

  describe('Populated dashboard (152C BUG_FRONTEND fix)', () => {
    it('Quick action labeled "Open workspace" replaces misleading "New notebook"', () => {
      renderPopulated()
      expect(screen.getByTestId('quick-action-open-workspace')).toBeTruthy()
      expect(screen.queryByRole('button', { name: /^new notebook$/i })).toBeNull()
    })

    it('Open workspace card mentions sidebar New Notebook as canonical create entry point', () => {
      renderPopulated()
      const card = screen.getByTestId('quick-action-open-workspace')
      expect(card.textContent).toMatch(/sidebar new notebook/i)
    })

    it('Open workspace card disabled without active workspace (DISABLED_BY_DESIGN)', () => {
      renderPopulated({ focusWorkspaceId: null })
      const card = screen.getByTestId('quick-action-open-workspace') as HTMLButtonElement
      expect(card.disabled).toBe(true)
    })

    it('Open workspace card fires onNewNotebook callback (navigation to workspace overview)', () => {
      const onNew = vi.fn()
      renderPopulated({ onNewNotebook: onNew })
      fireEvent.click(screen.getByTestId('quick-action-open-workspace'))
      expect(onNew).toHaveBeenCalled()
    })

    it('Add another workspace form is enabled with a name (WORKING)', () => {
      const onCreate = vi.fn()
      renderPopulated({ newWorkspaceName: 'Beta', onCreateWorkspace: onCreate })
      const button = screen.getByRole('button', { name: /add workspace/i }) as HTMLButtonElement
      expect(button.disabled).toBe(false)
      fireEvent.click(button)
      expect(onCreate).toHaveBeenCalled()
    })
  })

  describe('No token / secret leaks in dashboard surfaces', () => {
    it('empty dashboard markup does not contain JWT or Bearer patterns', () => {
      const { container } = renderEmpty({ showOnboarding: true })
      const text = container.textContent ?? ''
      expect(text).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/)
      expect(text).not.toMatch(/Bearer\s+[A-Za-z0-9._-]+/)
      expect(text).not.toMatch(/Authorization:/i)
    })

    it('populated dashboard markup does not contain JWT or Bearer patterns', () => {
      const { container } = renderPopulated()
      const text = container.textContent ?? ''
      expect(text).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/)
      expect(text).not.toMatch(/Bearer\s+[A-Za-z0-9._-]+/)
      expect(text).not.toMatch(/Authorization:/i)
    })
  })
})

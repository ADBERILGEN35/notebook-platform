import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { WorkspaceDashboardEmpty } from './components/WorkspaceDashboardEmpty'
import { GettingStartedPanel } from './components/GettingStartedPanel'
import { RecentlyViewedSection } from './components/RecentlyViewedSection'

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

describe('Faz 151B visual mismatch fix', () => {
  it('empty dashboard renders hero, quick actions and recently viewed sections', () => {
    renderEmpty()
    expect(
      screen.getByRole('heading', { level: 1, name: /welcome to your workspace/i }),
    ).toBeTruthy()
    expect(screen.getByText(/quick actions/i)).toBeTruthy()
    expect(screen.getByText(/recently viewed/i)).toBeTruthy()
    expect(screen.getByLabelText(/recently viewed placeholder/i)).toBeTruthy()
  })

  it('embeds onboarding panel inside dashboard when showOnboarding is true', () => {
    renderEmpty({ showOnboarding: true })
    expect(screen.getByTestId('getting-started-panel')).toBeTruthy()
    expect(
      screen.getByRole('heading', { level: 1, name: /welcome to your workspace/i }),
    ).toBeTruthy()
  })

  it('does not render onboarding panel when dismissed (showOnboarding=false)', () => {
    renderEmpty({ showOnboarding: false })
    expect(screen.queryByTestId('getting-started-panel')).toBeNull()
  })

  it('Get Started panel dismiss button fires callback', () => {
    const onDismiss = vi.fn()
    render(
      <GettingStartedPanel
        hasWorkspace={false}
        onDismiss={onDismiss}
        onFocusCreate={vi.fn()}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: /dismiss onboarding/i }))
    expect(onDismiss).toHaveBeenCalled()
  })

  it('Recently viewed section shows skeleton when no notebooks', () => {
    render(<RecentlyViewedSection notebooks={[]} focusWorkspaceId="w1" isLoading={false} />)
    expect(screen.getByLabelText(/recently viewed placeholder/i)).toBeTruthy()
    expect(screen.queryByTestId('recently-viewed-list')).toBeNull()
  })

  it('Recently viewed section shows real notebooks when present (no mock data)', () => {
    render(
      <MemoryRouter>
        <RecentlyViewedSection
          notebooks={[
            {
              id: 'n1',
              workspaceId: 'w1',
              name: 'Architecture',
              createdBy: 'u1',
              createdAt: '2026-01-01T00:00:00Z',
              updatedAt: new Date().toISOString(),
            },
          ]}
          focusWorkspaceId="w1"
          isLoading={false}
          viewAllHref="/app/workspaces/w1"
        />
      </MemoryRouter>,
    )
    expect(screen.getByTestId('recently-viewed-list')).toBeTruthy()
    expect(screen.getByRole('link', { name: /architecture/i })).toBeTruthy()
    expect(screen.getByRole('link', { name: /view all/i })).toBeTruthy()
  })

  it('does not expose tokens in empty dashboard markup', () => {
    const { container } = renderEmpty({ showOnboarding: true })
    const text = container.textContent ?? ''
    expect(text).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/)
    expect(text).not.toMatch(/Bearer\s+[A-Za-z0-9._-]+/)
  })
})

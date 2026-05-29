import type { ReactNode } from 'react'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { ErrorState } from '../../../shared/components/ErrorState'

type WorkspaceDashboardHeroProps = {
  workspaceName: string
  onWorkspaceNameChange: (value: string) => void
  onCreateWorkspace: () => void
  createPending: boolean
  createError?: boolean
  error?: unknown
  secondaryAction?: ReactNode
}

/**
 * Welcome hero card for the empty workspace dashboard.
 *
 * Visual reference: docs/design/workspace_dashboard_empty (Welcome to your
 * workspace hero with decorative blur and primary/secondary CTAs).
 *
 * Functional CTA is workspace creation because the API requires at least one
 * workspace before notebooks can exist.
 */
export function WorkspaceDashboardHero({
  workspaceName,
  onWorkspaceNameChange,
  onCreateWorkspace,
  createPending,
  createError,
  error,
  secondaryAction,
}: WorkspaceDashboardHeroProps) {
  return (
    <section
      className="relative h-full overflow-hidden rounded-2xl border border-outline-variant bg-surface-container-lowest p-6 shadow-card sm:p-8"
      aria-labelledby="workspace-dashboard-hero-title"
    >
      <div
        className="pointer-events-none absolute -right-16 -top-16 h-72 w-72 rounded-full bg-primary/10 blur-3xl"
        aria-hidden
      />
      <div
        className="pointer-events-none absolute -bottom-24 -left-24 h-72 w-72 rounded-full bg-primary-fixed/40 blur-3xl"
        aria-hidden
      />
      <div className="relative z-10 flex h-full flex-col">
        <h1
          id="workspace-dashboard-hero-title"
          className="font-display text-display-lg text-on-surface"
        >
          Welcome to your workspace
        </h1>
        <p className="mt-3 max-w-2xl text-body-lg text-on-surface-variant">
          Get started by creating your first workspace. You can then add notebooks from the
          sidebar, capture your first note, and invite teammates to collaborate.
        </p>
        <div className="mt-6 flex max-w-md flex-col gap-2 sm:flex-row sm:items-end">
          <Input
            placeholder="Workspace name"
            value={workspaceName}
            onChange={(event) => onWorkspaceNameChange(event.target.value)}
            aria-label="New workspace name"
            className="flex-1"
          />
        </div>
        <div className="mt-4 flex flex-wrap gap-3">
          <Button
            type="button"
            className="flex items-center gap-2 bg-primary text-on-primary shadow-sm hover:bg-primary-container hover:text-on-primary-container"
            disabled={!workspaceName.trim() || createPending}
            onClick={onCreateWorkspace}
            aria-label="Create new workspace"
          >
            <span aria-hidden>＋</span>
            {createPending ? 'Creating…' : 'Create New Workspace'}
          </Button>
          {secondaryAction}
        </div>
        {createError && error ? <ErrorState error={error} className="mt-4" /> : null}
      </div>
    </section>
  )
}

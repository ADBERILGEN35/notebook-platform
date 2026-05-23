import type { ReactNode } from 'react'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import { ErrorState } from '../../../shared/components/ErrorState'

type WorkspaceGettingStartedCardProps = {
  workspaceName: string
  onWorkspaceNameChange: (value: string) => void
  onCreateWorkspace: () => void
  createPending: boolean
  createError?: boolean
  error?: unknown
  onGuidedSetup?: () => void
  showGuidedSetup?: boolean
  secondaryAction?: ReactNode
}

export function WorkspaceGettingStartedCard({
  workspaceName,
  onWorkspaceNameChange,
  onCreateWorkspace,
  createPending,
  createError,
  error,
  onGuidedSetup,
  showGuidedSetup,
  secondaryAction,
}: WorkspaceGettingStartedCardProps) {
  return (
    <section
      className="relative overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest p-6 shadow-card sm:p-8"
      aria-labelledby="getting-started-title"
    >
      <div
        className="pointer-events-none absolute -right-10 -top-10 h-64 w-64 rounded-full bg-primary/5 blur-3xl"
        aria-hidden
      />
      <div className="relative z-10">
        <h1 id="getting-started-title" className="font-display text-display-lg text-on-surface">
          Welcome to your workspace
        </h1>
        <p className="mt-3 max-w-2xl text-body-lg text-on-surface-variant">
          Create a workspace to organize notebooks and notes. Then add your first notebook from the
          sidebar and capture your first note in the editor.
        </p>
        <ol className="mt-6 space-y-2 text-body-md text-on-surface-variant">
          <li>
            <span className="font-medium text-on-surface">1.</span> Name and create your workspace
          </li>
          <li>
            <span className="font-medium text-on-surface">2.</span> Add a notebook from the sidebar
          </li>
          <li>
            <span className="font-medium text-on-surface">3.</span> Write your first note
          </li>
        </ol>
        <div className="mt-6 flex max-w-md flex-col gap-2 sm:flex-row sm:items-end">
          <Input
            placeholder="Workspace name"
            value={workspaceName}
            onChange={(event) => onWorkspaceNameChange(event.target.value)}
            aria-label="New workspace name"
            className="flex-1"
          />
          <Button
            type="button"
            className="shrink-0 bg-primary text-on-primary hover:bg-primary-container"
            disabled={!workspaceName.trim() || createPending}
            onClick={onCreateWorkspace}
            aria-label="Create workspace"
          >
            {createPending ? 'Creating…' : 'Create workspace'}
          </Button>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          {showGuidedSetup && onGuidedSetup ? (
            <Button type="button" onClick={onGuidedSetup}>
              Guided setup
            </Button>
          ) : null}
          {secondaryAction}
        </div>
        {createError && error ? <ErrorState error={error} className="mt-4" /> : null}
      </div>
    </section>
  )
}

import { Link } from 'react-router-dom'
import type { Workspace } from '../types/api'
import { StatusBadge } from './StatusBadge'

type WorkspaceCardProps = {
  workspace: Workspace
  notebookCount?: number
  isActive?: boolean
  description?: string
}

function defaultDescription(workspace: Workspace): string {
  if (workspace.type === 'TEAM') {
    return 'Shared notebooks, tags, and member collaboration for your team.'
  }
  return 'Your personal space for notebooks and notes.'
}

export function WorkspaceCard({ workspace, notebookCount, isActive, description }: WorkspaceCardProps) {
  const blurb = description ?? defaultDescription(workspace)
  return (
    <Link
      to={`/app/workspaces/${workspace.id}`}
      className={`block rounded-xl border bg-surface-container-lowest p-4 shadow-card transition-all hover:border-primary/30 hover:shadow-auth-card focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
        isActive ? 'border-primary ring-2 ring-primary-fixed-dim' : 'border-outline-variant'
      }`}
      aria-current={isActive ? 'page' : undefined}
    >
      <header className="flex items-start justify-between gap-2">
        <div>
          <h3 className="font-display text-headline-sm text-on-surface">{workspace.name}</h3>
          <p className="mt-1 text-label-md text-on-surface-variant">@{workspace.slug}</p>
        </div>
        <StatusBadge label={workspace.type === 'TEAM' ? 'Team' : 'Personal'} tone="neutral" />
      </header>
      <p className="mt-3 line-clamp-2 text-body-md text-on-surface-variant">{blurb}</p>
      {typeof notebookCount === 'number' ? (
        <p className="mt-3 flex items-center gap-4 border-t border-outline-variant pt-3 text-label-md text-on-surface-variant">
          <span>{notebookCount} notebook{notebookCount === 1 ? '' : 's'}</span>
        </p>
      ) : null}
    </Link>
  )
}

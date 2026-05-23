import { Link } from 'react-router-dom'
import type { Notebook, Workspace } from '../../../shared/types/api'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingState } from '../../../shared/components/LoadingState'
import { Button } from '../../../shared/components/Button'
import { formatRelativeTime } from '../utils/format-relative-time'

type RecentWorkspaceActivityProps = {
  notebooks: Notebook[]
  workspaces: Workspace[]
  focusWorkspaceId: string | null
  isLoading: boolean
  onManageWorkspace: () => void
}

export function RecentWorkspaceActivity({
  notebooks,
  workspaces,
  focusWorkspaceId,
  isLoading,
  onManageWorkspace,
}: RecentWorkspaceActivityProps) {
  const workspaceById = new Map(workspaces.map((w) => [w.id, w]))

  if (!focusWorkspaceId) {
    return (
      <p className="text-body-md text-on-surface-variant">
        Select a workspace card above to see recent notebooks.
      </p>
    )
  }

  if (isLoading) {
    return <LoadingState label="Loading notebooks…" />
  }

  if (notebooks.length === 0) {
    return (
      <EmptyState
        title="No notebooks yet"
        message="Create a notebook from the sidebar or open workspace settings."
        actions={
          <Button type="button" onClick={onManageWorkspace}>
            Manage workspace
          </Button>
        }
      />
    )
  }

  return (
    <section aria-labelledby="recent-work-title">
      <header className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h2 id="recent-work-title" className="font-display text-headline-md text-on-surface">
          Recent work
        </h2>
        {focusWorkspaceId ? (
          <Link
            to={`/app/workspaces/${focusWorkspaceId}`}
            className="text-label-md font-medium text-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            View all
          </Link>
        ) : null}
      </header>
      <div className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest">
        <div
          className="hidden grid-cols-12 gap-2 border-b border-outline-variant bg-surface-container-low px-4 py-2 text-label-md uppercase tracking-wide text-on-surface-variant sm:grid"
          aria-hidden
        >
          <span className="col-span-6">Notebook</span>
          <span className="col-span-3">Last updated</span>
          <span className="col-span-3 text-right">Workspace</span>
        </div>
        <ul className="divide-y divide-outline-variant">
          {notebooks.map((notebook) => {
            const workspace = workspaceById.get(notebook.workspaceId)
            return (
              <li key={notebook.id}>
                <Link
                  to={`/app/notebooks/${notebook.id}`}
                  className="grid grid-cols-1 gap-2 px-4 py-3 transition-colors hover:bg-surface-container-low focus-visible:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary sm:grid-cols-12 sm:items-center"
                >
                  <span className="col-span-6 flex items-center gap-3">
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-fixed text-primary" aria-hidden>
                      📓
                    </span>
                    <span>
                      <span className="block font-medium text-on-surface">{notebook.name}</span>
                      <span className="block text-body-md text-on-surface-variant sm:hidden">
                        {workspace?.name ?? 'Workspace'}
                      </span>
                    </span>
                  </span>
                  <span className="col-span-3 text-body-md text-on-surface-variant">
                    {formatRelativeTime(notebook.updatedAt)}
                  </span>
                  <span className="col-span-3 text-body-md text-on-surface-variant sm:text-right">
                    {workspace?.name ?? '—'}
                  </span>
                </Link>
              </li>
            )
          })}
        </ul>
      </div>
    </section>
  )
}

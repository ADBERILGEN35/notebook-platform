import { Link } from 'react-router-dom'
import { useOnlineStatus } from '../../../shared/hooks/useOnlineStatus'

type WorkspaceHealthSummaryProps = {
  workspaceCount: number
  notebookCount?: number
}

export function WorkspaceHealthSummary({ workspaceCount, notebookCount }: WorkspaceHealthSummaryProps) {
  const { isOnline } = useOnlineStatus()

  return (
    <aside
      className="rounded-xl border border-outline-variant bg-surface-container-low p-4"
      aria-label="Workspace activity summary"
    >
      <h2 className="font-display text-headline-sm text-on-surface">At a glance</h2>
      <dl className="mt-3 grid gap-2 text-body-md">
        <div className="flex justify-between gap-2">
          <dt className="text-on-surface-variant">Connection</dt>
          <dd className="font-medium text-on-surface">{isOnline ? 'Online' : 'Offline'}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-on-surface-variant">Workspaces</dt>
          <dd className="font-medium text-on-surface">{workspaceCount}</dd>
        </div>
        {typeof notebookCount === 'number' ? (
          <div className="flex justify-between gap-2">
            <dt className="text-on-surface-variant">Notebooks (active)</dt>
            <dd className="font-medium text-on-surface">{notebookCount}</dd>
          </div>
        ) : null}
      </dl>
      <p className="mt-3 text-body-md text-on-surface-variant">
        {isOnline
          ? 'Changes sync through the API when services are available.'
          : 'You are offline. Drafts may be queued depending on your settings.'}
      </p>
      <Link
        to="/app/notifications"
        className="mt-3 inline-flex text-body-md font-medium text-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        Open notifications
      </Link>
    </aside>
  )
}

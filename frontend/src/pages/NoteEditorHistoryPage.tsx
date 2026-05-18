import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '../shared/components/PageHeader'
import { ResponsiveContent } from '../shared/components/ResponsiveContent'
import { VersionHistoryPanel } from '../features/note-editor/components/VersionHistoryPanel'
import { ActivityTimelinePanel } from '../features/note-editor/components/ActivityTimelinePanel'
import { Button } from '../shared/components/Button'

export function NoteEditorHistoryPage() {
  const { workspaceId, noteId } = useParams()
  const navigate = useNavigate()

  if (!workspaceId || !noteId) return null

  return (
    <ResponsiveContent>
      <PageHeader
        title="Note history"
        subtitle="Version snapshots and recent activity"
        actions={
          <Button type="button" onClick={() => navigate(`/app/workspaces/${workspaceId}/notes/${noteId}`)}>
            Back to editor
          </Button>
        }
      />
      <nav className="mb-4 text-label-md text-on-surface-variant" aria-label="Breadcrumb">
        <Link to={`/app/workspaces/${workspaceId}`} className="hover:text-primary">
          Workspace
        </Link>
        <span aria-hidden> / </span>
        <Link to={`/app/workspaces/${workspaceId}/notes/${noteId}`} className="hover:text-primary">
          Note
        </Link>
        <span aria-hidden> / </span>
        <span className="text-on-surface">History</span>
      </nav>
      <div className="grid gap-4 lg:grid-cols-2">
        <VersionHistoryPanel noteId={noteId} restoreDisabled />
        <ActivityTimelinePanel noteId={noteId} />
      </div>
    </ResponsiveContent>
  )
}

import { Link } from 'react-router-dom'
import { InlineStatus } from '../../../shared/components/InlineStatus'

type NoteEditorHeaderProps = {
  workspaceId: string
  noteTitle?: string
  saveStatusLabel?: string
  saveTone?: 'neutral' | 'success' | 'warning'
  onShare?: () => void
  onHistory?: () => void
}

export function NoteEditorHeader({
  workspaceId,
  noteTitle,
  saveStatusLabel,
  saveTone = 'neutral',
  onShare,
  onHistory,
}: NoteEditorHeaderProps) {
  return (
    <header className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-outline-variant pb-4">
      <nav aria-label="Breadcrumb" className="min-w-0 text-label-md text-on-surface-variant">
        <ol className="flex flex-wrap items-center gap-1">
          <li>
            <Link to="/app/workspaces" className="hover:text-primary focus-visible:outline-primary">
              Hub
            </Link>
          </li>
          <li aria-hidden>/</li>
          <li>
            <Link to={`/app/workspaces/${workspaceId}`} className="hover:text-primary focus-visible:outline-primary">
              Workspace
            </Link>
          </li>
          <li aria-hidden>/</li>
          <li className="truncate font-medium text-on-surface">{noteTitle || 'Note'}</li>
        </ol>
      </nav>
      <div className="flex flex-wrap items-center gap-2">
        {saveStatusLabel ? <InlineStatus label={saveStatusLabel} tone={saveTone} /> : null}
        {onHistory ? (
          <button
            type="button"
            className="rounded-lg border border-outline-variant px-3 py-1.5 text-label-md hover:bg-surface-container-low focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
            onClick={onHistory}
          >
            History
          </button>
        ) : null}
        {onShare ? (
          <button
            type="button"
            className="rounded-lg bg-primary px-3 py-1.5 text-label-md text-white hover:bg-primary-container focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            onClick={onShare}
          >
            Share
          </button>
        ) : null}
      </div>
    </header>
  )
}

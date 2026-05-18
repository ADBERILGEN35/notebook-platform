import type { SearchNoteResult } from '../../../shared/types/api'
import { PanelCard } from '../../../shared/components/PanelCard'
import { Button } from '../../../shared/components/Button'
import { Link } from 'react-router-dom'

type SearchPreviewDrawerProps = {
  result: SearchNoteResult | null
  workspaceId: string | null
  onClose: () => void
}

export function SearchPreviewDrawer({ result, workspaceId, onClose }: SearchPreviewDrawerProps) {
  if (!result || !workspaceId) {
    return (
      <PanelCard title="Preview" subtitle="Select a result">
        <p className="text-body-md text-on-surface-variant">Choose a search result to see a preview snippet.</p>
      </PanelCard>
    )
  }

  return (
    <PanelCard
      title={result.title}
      subtitle="Preview"
      footer={
        <div className="flex gap-2">
          <Button type="button" onClick={onClose}>
            Close
          </Button>
          <Link
            to={`/app/workspaces/${workspaceId}/notes/${result.noteId}`}
            className="inline-flex items-center rounded-md bg-primary px-3 py-2 text-label-md text-white hover:bg-primary-container"
          >
            Open note
          </Link>
        </div>
      }
    >
      <p className="whitespace-pre-wrap text-body-md text-on-surface-variant">{result.snippet}</p>
      {result.noteUpdatedAt ? (
        <p className="mt-2 text-label-md text-on-surface-variant">
          Updated {new Date(result.noteUpdatedAt).toLocaleString()}
        </p>
      ) : null}
    </PanelCard>
  )
}

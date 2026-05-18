import { Link } from 'react-router-dom'
import type { SearchNoteResult } from '../../../shared/types/api'

type SearchResultCardProps = {
  result: SearchNoteResult
  workspaceId: string
  selected?: boolean
  onSelect?: () => void
}

export function SearchResultCard({ result, workspaceId, selected, onSelect }: SearchResultCardProps) {
  const href = `/app/workspaces/${workspaceId}/notes/${result.noteId}`
  return (
    <article
      className={`rounded-xl border bg-surface-container-lowest p-4 transition ${
        selected ? 'border-primary ring-2 ring-primary-fixed-dim' : 'border-outline-variant hover:border-primary/30'
      }`}
    >
      <Link
        to={href}
        className="block focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        onClick={onSelect}
      >
        <h3 className="font-display text-headline-sm text-on-surface">{result.title}</h3>
        <p className="mt-2 line-clamp-3 text-body-md text-on-surface-variant">{result.snippet}</p>
        <p className="mt-2 text-label-md text-on-surface-variant">
          Results respect your workspace permissions. You only see notes you can access.
        </p>
      </Link>
    </article>
  )
}

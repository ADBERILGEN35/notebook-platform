import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useWorkspaceStore } from '../features/workspaces/workspace-store'
import { searchNotes } from '../features/search/search-api'
import { Input } from '../shared/components/Input'
import { EmptyState } from '../shared/components/EmptyState'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { PaginationControls } from '../shared/components/PaginationControls'
import { PageHeader } from '../shared/components/PageHeader'

export function SearchPage() {
  const workspaceId = useWorkspaceStore((state) => state.activeWorkspaceId)
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)

  const result = useQuery({
    queryKey: ['search', workspaceId, query, page],
    queryFn: () => searchNotes(workspaceId!, query, page, 20),
    enabled: Boolean(workspaceId && query.trim().length > 1),
  })

  return (
    <div className="space-y-3">
      <PageHeader title="Search" subtitle="Workspace-scoped note search" />
      <Input placeholder="Type at least 2 chars..." value={query} onChange={(event) => setQuery(event.target.value)} />
      {result.isError ? <ErrorAlert error={result.error} /> : null}
      {!query ? <EmptyState title="Start searching" message="Enter keywords to find notes." /> : null}
      {result.data && result.data.items.length === 0 ? (
        <EmptyState title="No result" message="No notes matched your query." />
      ) : null}
      <div className="space-y-2">
        {result.data?.items.map((item) => (
          <div key={item.noteId} className="rounded border border-slate-200 bg-white p-3">
            <h3 className="text-sm font-semibold">{item.title}</h3>
            <p className="text-sm text-slate-600">{item.snippet}</p>
          </div>
        ))}
      </div>
      {result.data ? (
        <PaginationControls
          page={result.data.page}
          hasNext={result.data.hasNext}
          hasPrevious={result.data.hasPrevious}
          onNext={() => setPage((current) => current + 1)}
          onPrevious={() => setPage((current) => Math.max(0, current - 1))}
        />
      ) : null}
    </div>
  )
}


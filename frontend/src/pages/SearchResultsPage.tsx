import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useWorkspaceStore } from '../features/workspaces/workspace-store'
import { searchNotes } from '../features/search/search-api'
import { pushRecentSearch } from '../features/search/use-recent-searches'
import { PageHeader } from '../shared/components/PageHeader'
import { ResponsiveContent } from '../shared/components/ResponsiveContent'
import { Input } from '../shared/components/Input'
import { EmptyState } from '../shared/components/EmptyState'
import { LoadingState } from '../shared/components/LoadingState'
import { ErrorState } from '../shared/components/ErrorState'
import { PaginationControls } from '../shared/components/PaginationControls'
import { SearchFilterPanel } from '../features/search/components/SearchFilterPanel'
import { SearchResultCard } from '../features/search/components/SearchResultCard'
import { SearchPreviewDrawer } from '../features/search/components/SearchPreviewDrawer'
import type { SearchNoteResult } from '../shared/types/api'

export function SearchResultsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const workspaceId = useWorkspaceStore((s) => s.activeWorkspaceId)
  const initialQ = searchParams.get('q') ?? ''
  const [query, setQuery] = useState(initialQ)
  const [page, setPage] = useState(0)
  const [sort, setSort] = useState('relevance')
  const [notebookFilter, setNotebookFilter] = useState('')
  const [selected, setSelected] = useState<SearchNoteResult | null>(null)

  useEffect(() => {
    setQuery(initialQ)
  }, [initialQ])

  const result = useQuery({
    queryKey: ['search', workspaceId, query, page],
    queryFn: () => searchNotes(workspaceId!, query, page, 20),
    enabled: Boolean(workspaceId && query.trim().length > 1),
  })

  const onSubmit = (value: string) => {
    const trimmed = value.trim()
    setSearchParams(trimmed ? { q: trimmed } : {})
    if (trimmed.length > 1) pushRecentSearch(trimmed)
    setPage(0)
    setSelected(null)
  }

  return (
    <ResponsiveContent>
      <PageHeader
        title="Search results"
        subtitle="Workspace-scoped, permission-filtered note search."
        actions={
          <Link to="/app/search/discover" className="text-body-md text-primary hover:underline">
            Discovery dashboard
          </Link>
        }
      />
      <div className="mb-4">
        <Input
          placeholder="Search notes (min 2 characters)…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') onSubmit(query)
          }}
          aria-label="Search query"
        />
      </div>
      {!workspaceId ? (
        <EmptyState title="Select a workspace" message="Choose a workspace to search notes you can access." />
      ) : null}
      <div className="grid gap-4 lg:grid-cols-[14rem_1fr_18rem]">
        <SearchFilterPanel sort={sort} onSortChange={setSort} notebookId={notebookFilter} onNotebookIdChange={setNotebookFilter} />
        <div>
          {result.isLoading ? <LoadingState label="Searching…" /> : null}
          {result.isError ? <ErrorState error={result.error} /> : null}
          {!query.trim() ? <EmptyState title="Enter a query" message="Type keywords to find notes." /> : null}
          {query.trim().length > 1 && result.data?.items.length === 0 && !result.isLoading ? (
            <EmptyState title="No results" message="Try different keywords or check another workspace." />
          ) : null}
          <ul className="space-y-3">
            {result.data?.items.map((item) => (
              <li key={item.noteId}>
                <SearchResultCard
                  result={item}
                  workspaceId={workspaceId!}
                  selected={selected?.noteId === item.noteId}
                  onSelect={() => setSelected(item)}
                />
              </li>
            ))}
          </ul>
          {result.data ? (
            <PaginationControls
              page={result.data.page}
              hasNext={result.data.hasNext}
              hasPrevious={result.data.hasPrevious}
              onNext={() => setPage((p) => p + 1)}
              onPrevious={() => setPage((p) => Math.max(0, p - 1))}
            />
          ) : null}
        </div>
        <SearchPreviewDrawer result={selected} workspaceId={workspaceId} onClose={() => setSelected(null)} />
      </div>
    </ResponsiveContent>
  )
}

/** @deprecated alias */
export const SearchPage = SearchResultsPage

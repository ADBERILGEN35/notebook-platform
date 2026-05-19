import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { searchNotes } from '../search-api'
import { useWorkspaceStore } from '../../workspaces/workspace-store'
import { pushRecentSearch, readRecentSearches } from '../use-recent-searches'
import { LoadingState } from '../../../shared/components/LoadingState'
import { EmptyState } from '../../../shared/components/EmptyState'
import { ErrorState } from '../../../shared/components/ErrorState'

type GlobalSearchOverlayProps = {
  open: boolean
  onClose: () => void
  initialQuery?: string
}

export function GlobalSearchOverlay({ open, onClose, initialQuery = '' }: GlobalSearchOverlayProps) {
  const navigate = useNavigate()
  const workspaceId = useWorkspaceStore((s) => s.activeWorkspaceId)
  const [query, setQuery] = useState(initialQuery)
  const [activeIndex, setActiveIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const recent = readRecentSearches()

  useEffect(() => {
    if (open) {
      setQuery(initialQuery)
      setActiveIndex(0)
      requestAnimationFrame(() => inputRef.current?.focus())
    }
  }, [open, initialQuery])

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        onClose()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  const searchQuery = useQuery({
    queryKey: ['search-overlay', workspaceId, query],
    queryFn: () => searchNotes(workspaceId!, query, 0, 8),
    enabled: Boolean(open && workspaceId && query.trim().length > 1),
  })

  const items = searchQuery.data?.items ?? []
  const groupedRecent = recent.length > 0 && query.trim().length < 2

  const goToSearch = (q: string) => {
    pushRecentSearch(q)
    onClose()
    navigate(`/app/search?q=${encodeURIComponent(q)}`)
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActiveIndex((i) => Math.min(i + 1, Math.max(0, items.length - 1)))
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActiveIndex((i) => Math.max(0, i - 1))
    }
    if (e.key === 'Enter' && query.trim()) {
      e.preventDefault()
      if (items[activeIndex] && workspaceId) {
        pushRecentSearch(query)
        onClose()
        navigate(`/app/workspaces/${workspaceId}/notes/${items[activeIndex].noteId}`)
        return
      }
      goToSearch(query.trim())
    }
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-on-surface/40 p-4 pt-[10vh] backdrop-blur-sm" role="presentation">
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Global search"
        data-testid="global-search-overlay"
        className="w-full max-w-2xl overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-auth-card"
      >
        <div className="border-b border-outline-variant px-4 py-3">
          <label className="sr-only" htmlFor="global-search-input">
            Search
          </label>
          <input
            ref={inputRef}
            id="global-search-input"
            type="search"
            className="w-full bg-transparent text-body-lg text-on-surface outline-none placeholder:text-on-surface-variant"
            placeholder="Search notes… (Ctrl+K)"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              setActiveIndex(0)
            }}
            onKeyDown={onKeyDown}
          />
          <p className="mt-1 text-label-md text-on-surface-variant">
            ↑↓ navigate · Enter open · Esc close · Results are permission-filtered
          </p>
        </div>
        <div className="max-h-[50vh] overflow-y-auto p-2">
          {!workspaceId ? (
            <EmptyState title="No workspace selected" message="Choose a workspace to search notes." />
          ) : null}
          {groupedRecent ? (
            <section aria-label="Recent searches" className="mb-2">
              <h2 className="px-2 py-1 text-label-md font-semibold uppercase text-on-surface-variant">Recent</h2>
              <ul>
                {recent.map((term) => (
                  <li key={term}>
                    <button
                      type="button"
                      className="w-full rounded-lg px-3 py-2 text-left text-body-md hover:bg-surface-container-low focus-visible:bg-surface-container-low focus-visible:outline-none"
                      onClick={() => goToSearch(term)}
                    >
                      {term}
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
          {query.trim().length > 1 && searchQuery.isLoading ? <LoadingState label="Searching…" /> : null}
          {query.trim().length > 1 && searchQuery.isError ? <ErrorState error={searchQuery.error} /> : null}
          {query.trim().length > 1 && items.length === 0 && !searchQuery.isLoading ? (
            <EmptyState title="No matches" message="Try different keywords." />
          ) : null}
          {items.length > 0 ? (
            <section aria-label="Search results">
              <h2 className="px-2 py-1 text-label-md font-semibold uppercase text-on-surface-variant">Notes</h2>
              <ul>
                {items.map((item, index) => (
                  <li key={item.noteId}>
                    <button
                      type="button"
                      className={`w-full rounded-lg px-3 py-2 text-left focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary ${
                        index === activeIndex ? 'bg-primary-fixed text-primary' : 'hover:bg-surface-container-low'
                      }`}
                      onClick={() => {
                        if (!workspaceId) return
                        pushRecentSearch(query)
                        onClose()
                        navigate(`/app/workspaces/${workspaceId}/notes/${item.noteId}`)
                      }}
                    >
                      <span className="font-medium">{item.title}</span>
                      <span className="mt-0.5 block line-clamp-1 text-label-md opacity-80">{item.snippet}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
          {query.trim().length > 1 ? (
            <button
              type="button"
              className="mt-2 w-full rounded-lg border border-dashed border-outline-variant px-3 py-2 text-body-md text-primary hover:bg-surface-container-low"
              onClick={() => goToSearch(query.trim())}
            >
              View all results for “{query.trim()}”
            </button>
          ) : null}
        </div>
      </div>
    </div>
  )
}

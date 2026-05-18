import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useWorkspaceStore } from '../features/workspaces/workspace-store'
import { listWorkspaces } from '../features/workspaces/workspace-api'
import { readRecentSearches } from '../features/search/use-recent-searches'
import { readSavedSearches, removeSavedSearch, saveSearch } from '../features/search/use-saved-searches'
import { PageHeader } from '../shared/components/PageHeader'
import { ResponsiveContent } from '../shared/components/ResponsiveContent'
import { DiscoveryCard } from '../features/search/components/DiscoveryCard'
import { Button } from '../shared/components/Button'
import { Input } from '../shared/components/Input'
import { LoadingState } from '../shared/components/LoadingState'
import { ErrorState } from '../shared/components/ErrorState'
import { InlineStatus } from '../shared/components/InlineStatus'
import { useOnlineStatus } from '../shared/hooks/useOnlineStatus'

export function SearchDiscoveryPage() {
  const workspaceId = useWorkspaceStore((s) => s.activeWorkspaceId)
  const { isOnline } = useOnlineStatus()
  const [saved, setSaved] = useState(readSavedSearches())
  const [saveLabel, setSaveLabel] = useState('')
  const recent = readRecentSearches()

  const workspacesQuery = useQuery({
    queryKey: ['workspaces'],
    queryFn: () => listWorkspaces(0, 10),
  })

  return (
    <ResponsiveContent>
      <PageHeader
        title="Search & discovery"
        subtitle="Saved searches, recent queries, and workspace exploration."
        actions={
          <Link to="/app/search" className="text-body-md text-primary hover:underline">
            Open search
          </Link>
        }
      />
      {!isOnline ? (
        <DiscoveryCard title="Indexing notice" description="You are offline">
          <InlineStatus label="Search index may be stale" tone="warning" />
          <p className="mt-2 text-body-md text-on-surface-variant">
            Cached results may appear until you reconnect.
          </p>
        </DiscoveryCard>
      ) : null}
      <div className="grid gap-4 lg:grid-cols-2">
        <DiscoveryCard title="Recent searches">
          {recent.length === 0 ? (
            <p className="text-body-md text-on-surface-variant">No recent searches yet.</p>
          ) : (
            <ul className="space-y-1">
              {recent.map((term) => (
                <li key={term}>
                  <Link to={`/app/search?q=${encodeURIComponent(term)}`} className="text-primary hover:underline">
                    {term}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </DiscoveryCard>
        <DiscoveryCard
          title="Saved searches"
          actions={
            <div className="flex flex-wrap gap-2">
              <Input
                placeholder="Label"
                value={saveLabel}
                onChange={(e) => setSaveLabel(e.target.value)}
                className="max-w-[8rem]"
                aria-label="Saved search label"
              />
              <Button
                type="button"
                className="text-label-md"
                disabled={!saveLabel.trim()}
                onClick={() => {
                  setSaved(saveSearch(saveLabel, saveLabel))
                  setSaveLabel('')
                }}
              >
                Save current
              </Button>
            </div>
          }
        >
          {saved.length === 0 ? (
            <p className="text-body-md text-on-surface-variant">Save frequent queries for quick access.</p>
          ) : (
            <ul className="space-y-2">
              {saved.map((item) => (
                <li key={item.id} className="flex justify-between gap-2">
                  <Link to={`/app/search?q=${encodeURIComponent(item.query)}`} className="text-primary hover:underline">
                    {item.label}
                  </Link>
                  <button type="button" className="text-label-md text-on-surface-variant hover:text-error" onClick={() => setSaved(removeSavedSearch(item.id))}>
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}
        </DiscoveryCard>
        <DiscoveryCard title="Workspaces">
          {workspacesQuery.isLoading ? <LoadingState label="Loading…" /> : null}
          {workspacesQuery.isError ? <ErrorState error={workspacesQuery.error} /> : null}
          <ul className="space-y-1">
            {workspacesQuery.data?.items.map((ws) => (
              <li key={ws.id}>
                <Link
                  to={`/app/workspaces/${ws.id}`}
                  className={ws.id === workspaceId ? 'font-medium text-primary' : 'text-on-surface hover:text-primary'}
                >
                  {ws.name}
                </Link>
              </li>
            ))}
          </ul>
        </DiscoveryCard>
        <DiscoveryCard title="Suggested" description="Open notes from your active workspace hub.">
          <Link to={workspaceId ? `/app/workspaces/${workspaceId}` : '/app/workspaces'} className="text-primary hover:underline">
            Browse workspace hub
          </Link>
        </DiscoveryCard>
      </div>
    </ResponsiveContent>
  )
}

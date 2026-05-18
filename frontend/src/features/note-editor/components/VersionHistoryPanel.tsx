import { useQuery } from '@tanstack/react-query'
import { listVersions } from '../../versions/versions-api'
import { VersionItem } from '../../../shared/components/VersionItem'
import { LoadingState } from '../../../shared/components/LoadingState'
import { EmptyState } from '../../../shared/components/EmptyState'
import { ErrorState } from '../../../shared/components/ErrorState'
import { PanelCard } from '../../../shared/components/PanelCard'

type VersionHistoryPanelProps = {
  noteId: string
  onRestore?: (versionNumber: number) => void
  restoreDisabled?: boolean
}

export function VersionHistoryPanel({ noteId, onRestore, restoreDisabled }: VersionHistoryPanelProps) {
  const query = useQuery({
    queryKey: ['note-versions', noteId],
    queryFn: () => listVersions(noteId, 0, 20),
    enabled: Boolean(noteId),
  })

  return (
    <PanelCard title="Version history" subtitle="Snapshots of this note">
      {query.isLoading ? <LoadingState label="Loading versions…" /> : null}
      {query.isError ? <ErrorState error={query.error} /> : null}
      {query.data?.items.length === 0 && !query.isLoading ? (
        <EmptyState title="No versions yet" message="Edits will appear here as new versions are saved." />
      ) : null}
      {query.data?.items.length ? (
        <ul className="space-y-2">
          {query.data.items.map((version) => (
            <VersionItem
              key={version.id}
              versionNumber={version.versionNumber}
              title={version.title}
              createdAt={version.createdAt}
              onRestore={onRestore ? () => onRestore(version.versionNumber) : undefined}
              restoreDisabled={restoreDisabled}
            />
          ))}
        </ul>
      ) : null}
    </PanelCard>
  )
}

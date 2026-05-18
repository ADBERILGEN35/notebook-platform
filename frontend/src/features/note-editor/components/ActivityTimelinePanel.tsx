import { useQuery } from '@tanstack/react-query'
import { listComments } from '../../comments/comments-api'
import { listVersions } from '../../versions/versions-api'
import { TimelineItem } from '../../../shared/components/TimelineItem'
import { LoadingState } from '../../../shared/components/LoadingState'
import { EmptyState } from '../../../shared/components/EmptyState'
import { ErrorState } from '../../../shared/components/ErrorState'
import { PanelCard } from '../../../shared/components/PanelCard'

type ActivityTimelinePanelProps = {
  noteId: string
}

export function ActivityTimelinePanel({ noteId }: ActivityTimelinePanelProps) {
  const commentsQuery = useQuery({
    queryKey: ['note-comments', noteId, 'timeline'],
    queryFn: () => listComments(noteId, 0, 10),
    enabled: Boolean(noteId),
  })

  const versionsQuery = useQuery({
    queryKey: ['note-versions', noteId, 'timeline'],
    queryFn: () => listVersions(noteId, 0, 10),
    enabled: Boolean(noteId),
  })

  const loading = commentsQuery.isLoading || versionsQuery.isLoading
  const error = commentsQuery.error || versionsQuery.error

  const events = [
    ...(versionsQuery.data?.items.map((v) => ({
      id: `version-${v.id}`,
      title: `Version ${v.versionNumber} saved`,
      timestamp: v.createdAt,
      description: v.title,
    })) ?? []),
    ...(commentsQuery.data?.items.map((c) => ({
      id: `comment-${c.id}`,
      title: c.resolvedAt ? 'Comment resolved' : 'Comment added',
      timestamp: c.createdAt,
      description: c.content.slice(0, 120),
    })) ?? []),
  ].sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())

  return (
    <PanelCard title="Activity" subtitle="Recent changes on this note">
      {loading ? <LoadingState label="Loading activity…" /> : null}
      {error ? <ErrorState error={error} /> : null}
      {!loading && !error && events.length === 0 ? (
        <EmptyState title="No activity yet" message="Comments and version saves will show up here." />
      ) : null}
      {events.length > 0 ? (
        <ol className="space-y-0">
          {events.map((event) => (
            <TimelineItem key={event.id} title={event.title} timestamp={event.timestamp} description={event.description} />
          ))}
        </ol>
      ) : null}
    </PanelCard>
  )
}

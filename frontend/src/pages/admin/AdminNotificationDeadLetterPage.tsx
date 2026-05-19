import { useCallback, useEffect, useState } from 'react'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_NOTIFICATIONS_DEAD_LETTER_READ,
  PERM_NOTIFICATIONS_DEAD_LETTER_REQUEUE,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import { isNotificationDeadLetterUiEnabled } from '../../shared/config/admin-feature-flags'
import { fetchDeadLetterList, type DeadLetterListItem } from '../../features/admin/notification-dead-letter-api'
import { DeadLetterEventTable } from '../../features/admin/notifications/DeadLetterEventTable'
import { OperationalRunbookLink } from '../../features/admin/notifications/OperationalRunbookLink'

export function AdminNotificationDeadLetterPage() {
  const user = useAuthStore((s) => s.user)
  const canRead = hasPlatformPermission(user, PERM_NOTIFICATIONS_DEAD_LETTER_READ)
  const canRequeue = hasPlatformPermission(user, PERM_NOTIFICATIONS_DEAD_LETTER_REQUEUE)
  const [items, setItems] = useState<DeadLetterListItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [eventType, setEventType] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const r = await fetchDeadLetterList({
        eventType: eventType.trim() || undefined,
        page,
        size: 20,
        sort: 'createdAt,desc',
      })
      setItems(r.items)
      setTotal(r.totalElements)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to load dead-letter queue')
      setItems([])
    } finally {
      setLoading(false)
    }
  }, [page, eventType])

  useEffect(() => {
    if (!canRead || !isNotificationDeadLetterUiEnabled()) return
    void load()
  }, [canRead, load, page])

  if (!isNotificationDeadLetterUiEnabled()) {
    return (
      <div className="space-y-3">
        <PageHeader title="Notification dead-letter" subtitle="Fanout outbox DEAD rows" />
        <Card className="p-4 text-sm text-slate-600">
          Dead-letter UI is disabled. Set FRONTEND_NOTIFICATION_DEAD_LETTER_ENABLED in runtime config.
        </Card>
      </div>
    )
  }

  if (!canRead) {
    return (
      <div className="space-y-3">
        <PageHeader title="Notification dead-letter" subtitle="Fanout outbox DEAD rows" />
        <Card className="p-4 text-sm text-slate-600">You do not have permission to view dead-letter records.</Card>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="Notification dead-letter"
        subtitle="Fanout SSE outbox — DEAD only. No message bodies or user identifiers (hashed recipient)."
      />
      <div className="flex flex-wrap items-end gap-2">
        <label className="flex flex-col text-xs">
          <span className="font-semibold text-slate-600">Event type</span>
          <input
            className="rounded border border-slate-200 px-2 py-1 text-sm"
            value={eventType}
            onChange={(e) => setEventType(e.target.value)}
            placeholder="e.g. notification.created"
          />
        </label>
        <button
          type="button"
          className="rounded bg-slate-800 px-3 py-1.5 text-sm text-white"
          onClick={() => {
            setPage(0)
            void load()
          }}
        >
          Apply filters
        </button>
        <span className="text-xs text-slate-500">Total: {total}</span>
      </div>
      {error ? <ErrorAlert message={error} /> : null}
      {loading ? <LoadingState label="Loading dead-letter rows" /> : null}
      {!loading ? (
        <Card className="p-0">
          <DeadLetterEventTable items={items} showRequeueLink={canRequeue} />
        </Card>
      ) : null}
      <div className="flex gap-2">
        <button
          type="button"
          disabled={page <= 0}
          className="rounded border px-2 py-1 text-xs disabled:opacity-40"
          onClick={() => setPage((p) => Math.max(0, p - 1))}
        >
          Previous
        </button>
        <button
          type="button"
          disabled={(page + 1) * 20 >= total}
          className="rounded border px-2 py-1 text-xs disabled:opacity-40"
          onClick={() => setPage((p) => p + 1)}
        >
          Next
        </button>
      </div>
      <OperationalRunbookLink docPath="docs/notification-dead-letter-requeue.md" />
    </div>
  )
}

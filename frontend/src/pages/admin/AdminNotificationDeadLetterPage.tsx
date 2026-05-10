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
import {
  dryRunDeadLetterRequeue,
  fetchDeadLetterList,
  requeueDeadLetter,
  type DeadLetterListItem,
  type RequeueDryRunResponse,
} from '../../features/admin/notification-dead-letter-api'

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
  const [selected, setSelected] = useState<DeadLetterListItem | null>(null)
  const [dryRun, setDryRun] = useState<RequeueDryRunResponse | null>(null)
  const [dryRunLoading, setDryRunLoading] = useState(false)
  const [requeueOpen, setRequeueOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())
  const [requeueError, setRequeueError] = useState<string | null>(null)

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

  async function runDryRun(row: DeadLetterListItem) {
    setSelected(row)
    setDryRun(null)
    setDryRunLoading(true)
    setRequeueError(null)
    try {
      const r = await dryRunDeadLetterRequeue(row.id)
      setDryRun(r)
    } catch (e: unknown) {
      setRequeueError(e instanceof Error ? e.message : 'Dry-run failed')
    } finally {
      setDryRunLoading(false)
    }
  }

  async function submitRequeue() {
    if (!selected) return
    setRequeueError(null)
    try {
      await requeueDeadLetter(selected.id, { idempotencyKey, reason: reason.trim() })
      setRequeueOpen(false)
      setReason('')
      setIdempotencyKey(crypto.randomUUID())
      setDryRun(null)
      setSelected(null)
      await load()
    } catch (e: unknown) {
      setRequeueError(e instanceof Error ? e.message : 'Requeue failed')
    }
  }

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
        subtitle="Fanout SSE outbox — DEAD only. No message bodies or user identifiers (hashed recipient). Requeue requires MFA-verified session when gateway enforces admin-write MFA."
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
      {!loading && items.length === 0 ? (
        <Card className="p-4 text-sm text-slate-600">No DEAD fanout rows in this page.</Card>
      ) : null}
      {!loading && items.length > 0 ? (
        <Card className="overflow-x-auto p-0">
          <table className="min-w-full text-left text-xs">
            <thead className="border-b bg-slate-50 text-slate-600">
              <tr>
                <th className="px-3 py-2">Event</th>
                <th className="px-3 py-2">Attempts</th>
                <th className="px-3 py-2">Requeues</th>
                <th className="px-3 py-2">Error</th>
                <th className="px-3 py-2">Dead at</th>
                <th className="px-3 py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.id} className="border-b border-slate-100">
                  <td className="px-3 py-2 font-mono">{row.eventType}</td>
                  <td className="px-3 py-2">{row.attemptCount}</td>
                  <td className="px-3 py-2">{row.requeueCount}</td>
                  <td className="max-w-xs truncate px-3 py-2" title={row.lastErrorSummary}>
                    <span className="font-mono text-slate-700">{row.lastErrorCode}</span>{' '}
                    {row.lastErrorSummary}
                  </td>
                  <td className="px-3 py-2 text-slate-600">{row.deadAt ?? '—'}</td>
                  <td className="space-x-2 px-3 py-2">
                    <button
                      type="button"
                      className="text-primary-700 underline"
                      onClick={() => void runDryRun(row)}
                    >
                      Dry-run
                    </button>
                    {canRequeue ? (
                      <button
                        type="button"
                        className="text-primary-700 underline"
                        onClick={() => {
                          setSelected(row)
                          setDryRun(null)
                          setRequeueOpen(true)
                          setReason('')
                          setIdempotencyKey(crypto.randomUUID())
                          setRequeueError(null)
                        }}
                      >
                        Requeue
                      </button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
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

      {selected && (dryRun || dryRunLoading || requeueOpen) ? (
        <Card className="space-y-2 p-4">
          <p className="text-xs font-semibold uppercase text-slate-500">Selected row</p>
          <p className="font-mono text-sm">{selected.id}</p>
          {dryRunLoading ? <p className="text-xs text-slate-600">Running dry-run…</p> : null}
          {dryRun ? (
            <div className="space-y-1 text-sm">
              <p>
                <span className="font-semibold">Can requeue:</span>{' '}
                {dryRun.canRequeue ? 'yes' : 'no'}
              </p>
              <p className="text-slate-700">{dryRun.impact.reason}</p>
              <ul className="list-inside list-disc text-xs text-slate-600">
                {dryRun.checks.map((c) => (
                  <li key={c.code}>
                    {c.code}: {c.passed ? 'ok' : 'failed'}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
          {requeueOpen && canRequeue ? (
            <div className="space-y-2 border-t border-slate-100 pt-3">
              <p className="text-xs text-amber-800">
                Confirm requeue: duplicates may cause extra SSE events (clients should be idempotent). MFA may be
                required.
              </p>
              {selected.requeueCount > 0 ? (
                <p className="text-xs font-semibold text-amber-900">
                  This row was already requeued {selected.requeueCount} time(s). Proceed only if intentional.
                </p>
              ) : null}
              <label className="flex flex-col text-xs">
                <span className="font-semibold text-slate-600">Reason (required, min 5 chars)</span>
                <textarea
                  className="min-h-[72px] rounded border border-slate-200 px-2 py-1 text-sm"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </label>
              {requeueError ? <p className="text-xs text-red-700">{requeueError}</p> : null}
              <div className="flex gap-2">
                <button
                  type="button"
                  className="rounded bg-slate-800 px-3 py-1.5 text-sm text-white disabled:opacity-40"
                  disabled={reason.trim().length < 5}
                  onClick={() => void submitRequeue()}
                >
                  Submit requeue
                </button>
                <button
                  type="button"
                  className="rounded border px-3 py-1.5 text-sm"
                  onClick={() => {
                    setRequeueOpen(false)
                    setRequeueError(null)
                  }}
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : null}
        </Card>
      ) : null}
      <p className="text-xs text-slate-500">Runbook: docs/notification-dead-letter-requeue.md</p>
    </div>
  )
}

import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_NOTIFICATIONS_LEGAL_HOLD_READ,
  PERM_NOTIFICATIONS_LEGAL_HOLD_WRITE,
  PERM_NOTIFICATIONS_RETENTION_READ,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import { isNotificationLegalHoldUiEnabled, isNotificationRetentionUiEnabled } from '../../shared/config/admin-feature-flags'
import {
  createLegalHold,
  fetchLegalHolds,
  releaseLegalHold,
  type LegalHoldResponse,
  type LegalHoldScope,
} from '../../features/admin/notification-legal-hold-api'

const SCOPES: LegalHoldScope[] = [
  'ALL_NOTIFICATION_RETENTION',
  'FANOUT_OUTBOX',
  'DEAD_LETTER_REQUEUE_REQUESTS',
  'ANALYTICS',
  'DIGEST_ITEMS',
  'EMAIL_NOTIFICATIONS',
]

export function AdminNotificationLegalHoldsPage() {
  const user = useAuthStore((s) => s.user)
  const canRead = hasPlatformPermission(user, PERM_NOTIFICATIONS_LEGAL_HOLD_READ)
  const canWrite = hasPlatformPermission(user, PERM_NOTIFICATIONS_LEGAL_HOLD_WRITE)
  const showRetentionLink =
    isNotificationRetentionUiEnabled() && hasPlatformPermission(user, PERM_NOTIFICATIONS_RETENTION_READ)

  const [holds, setHolds] = useState<LegalHoldResponse[]>([])
  const [filter, setFilter] = useState<'ACTIVE' | 'RELEASED' | 'ALL'>('ACTIVE')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [releaseHold, setReleaseHold] = useState<LegalHoldResponse | null>(null)
  const [holdKey, setHoldKey] = useState('')
  const [scope, setScope] = useState<LegalHoldScope>('FANOUT_OUTBOX')
  const [reason, setReason] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [releaseReason, setReleaseReason] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const r =
        filter === 'ALL'
          ? await fetchLegalHolds()
          : await fetchLegalHolds(filter === 'ACTIVE' ? 'ACTIVE' : 'RELEASED')
      setHolds(r.holds)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to load legal holds')
      setHolds([])
    } finally {
      setLoading(false)
    }
  }, [filter])

  useEffect(() => {
    if (!canRead || !isNotificationLegalHoldUiEnabled()) return
    void load()
  }, [canRead, load])

  const onCreate = async () => {
    if (reason.trim().length < 10) {
      setError('Reason must be at least 10 characters.')
      return
    }
    if (!holdKey.trim()) {
      setError('holdKey is required.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await createLegalHold({
        holdKey: holdKey.trim(),
        scope,
        reason: reason.trim(),
        expiresAt: expiresAt.trim() ? new Date(expiresAt).toISOString() : undefined,
      })
      setCreateOpen(false)
      setHoldKey('')
      setReason('')
      setExpiresAt('')
      await load()
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Create failed')
    } finally {
      setBusy(false)
    }
  }

  const onRelease = async () => {
    if (!releaseHold) return
    if (releaseReason.trim().length < 10) {
      setError('Release reason must be at least 10 characters.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await releaseLegalHold(releaseHold.id, releaseReason.trim())
      setReleaseHold(null)
      setReleaseReason('')
      await load()
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Release failed')
    } finally {
      setBusy(false)
    }
  }

  if (!isNotificationLegalHoldUiEnabled()) {
    return (
      <div className="space-y-3">
        <PageHeader title="Legal holds" subtitle="Notification retention governance" />
        <Card className="p-4 text-sm text-slate-600">
          Legal holds UI is disabled. Set FRONTEND_NOTIFICATION_LEGAL_HOLD_ENABLED in runtime config.
        </Card>
      </div>
    )
  }

  if (!canRead) {
    return (
      <div className="space-y-3">
        <PageHeader title="Legal holds" subtitle="Notification retention governance" />
        <Card className="p-4 text-sm text-slate-600">You do not have permission to view legal holds.</Card>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="Legal holds"
        subtitle="Active holds block destructive retention purge for the selected scope. No notification payloads are stored here."
      />
      {showRetentionLink ? (
        <p className="text-sm text-slate-600">
          <Link to="/app/admin/notifications/retention" className="text-primary-700 underline">
            Notification retention plan
          </Link>
        </p>
      ) : null}
      <Card className="border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
        Active holds block destructive retention purge for selected targets. A past expiresAt does not auto-release;
        an admin must release the hold explicitly.
      </Card>
      <div className="flex flex-wrap items-center gap-2">
        <label className="text-sm text-slate-600">
          Filter{' '}
          <select
            className="ml-1 rounded border border-slate-300 px-2 py-1 text-sm"
            value={filter}
            onChange={(e) => setFilter(e.target.value as typeof filter)}
          >
            <option value="ACTIVE">Active</option>
            <option value="RELEASED">Released</option>
            <option value="ALL">All</option>
          </select>
        </label>
        <button
          type="button"
          className="rounded bg-slate-800 px-3 py-1.5 text-sm font-medium text-white"
          onClick={() => void load()}
          disabled={loading}
        >
          Refresh
        </button>
        {canWrite ? (
          <button
            type="button"
            className="rounded border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
            onClick={() => setCreateOpen(true)}
          >
            Create hold…
          </button>
        ) : null}
      </div>
      {error ? <ErrorAlert message={error} /> : null}
      {loading && holds.length === 0 ? <LoadingState label="Loading legal holds" /> : null}
      <Card className="overflow-x-auto p-0">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-3 py-2">Key</th>
              <th className="px-3 py-2">Scope</th>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2">Created</th>
              <th className="px-3 py-2">Expires</th>
              {canWrite ? <th className="px-3 py-2">Actions</th> : null}
            </tr>
          </thead>
          <tbody>
            {holds.map((h) => (
              <tr key={h.id} className="border-b border-slate-100">
                <td className="px-3 py-2 font-mono text-xs">{h.holdKey}</td>
                <td className="px-3 py-2 text-xs">{h.scope}</td>
                <td className="px-3 py-2">{h.status}</td>
                <td className="px-3 py-2 text-xs text-slate-600">{h.createdAt}</td>
                <td className="px-3 py-2 text-xs text-slate-600">{h.expiresAt ?? '—'}</td>
                {canWrite ? (
                  <td className="px-3 py-2">
                    {h.status === 'ACTIVE' ? (
                      <button
                        type="button"
                        className="text-sm text-primary-700 underline"
                        onClick={() => setReleaseHold(h)}
                      >
                        Release
                      </button>
                    ) : (
                      '—'
                    )}
                  </td>
                ) : null}
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      {createOpen ? (
        <Card className="space-y-3 border-slate-200 p-4">
          <p className="text-sm font-semibold text-slate-900">Create legal hold</p>
          <label className="block text-xs font-medium text-slate-600">
            holdKey
            <input
              className="mt-1 w-full rounded border border-slate-300 p-2 text-sm"
              value={holdKey}
              onChange={(e) => setHoldKey(e.target.value)}
            />
          </label>
          <label className="block text-xs font-medium text-slate-600">
            Scope
            <select
              className="mt-1 w-full rounded border border-slate-300 p-2 text-sm"
              value={scope}
              onChange={(e) => setScope(e.target.value as LegalHoldScope)}
            >
              {SCOPES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-xs font-medium text-slate-600">
            Reason (min 10 characters)
            <textarea
              className="mt-1 w-full rounded border border-slate-300 p-2 text-sm"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <label className="block text-xs font-medium text-slate-600">
            expiresAt (optional, UTC local — browser interprets)
            <input
              type="datetime-local"
              className="mt-1 w-full rounded border border-slate-300 p-2 text-sm"
              value={expiresAt}
              onChange={(e) => setExpiresAt(e.target.value)}
            />
          </label>
          <div className="flex gap-2">
            <button
              type="button"
              className="rounded bg-slate-800 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              disabled={busy}
              onClick={() => void onCreate()}
            >
              Create
            </button>
            <button
              type="button"
              className="rounded border border-slate-300 px-3 py-1.5 text-sm"
              disabled={busy}
              onClick={() => setCreateOpen(false)}
            >
              Cancel
            </button>
          </div>
        </Card>
      ) : null}

      {releaseHold ? (
        <Card className="space-y-3 border-slate-200 p-4">
          <p className="text-sm font-semibold text-slate-900">Release hold {releaseHold.holdKey}</p>
          <label className="block text-xs font-medium text-slate-600">
            Release reason (min 10 characters)
            <textarea
              className="mt-1 w-full rounded border border-slate-300 p-2 text-sm"
              rows={3}
              value={releaseReason}
              onChange={(e) => setReleaseReason(e.target.value)}
            />
          </label>
          <div className="flex gap-2">
            <button
              type="button"
              className="rounded bg-slate-800 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              disabled={busy}
              onClick={() => void onRelease()}
            >
              Release hold
            </button>
            <button
              type="button"
              className="rounded border border-slate-300 px-3 py-1.5 text-sm"
              disabled={busy}
              onClick={() => setReleaseHold(null)}
            >
              Cancel
            </button>
          </div>
        </Card>
      ) : null}
    </div>
  )
}

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_NOTIFICATIONS_ANALYTICS_READ,
  PERM_NOTIFICATIONS_DEAD_LETTER_READ,
  PERM_NOTIFICATIONS_RETENTION_READ,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import {
  isNotificationAnalyticsUiEnabled,
  isNotificationDeadLetterUiEnabled,
  isNotificationRetentionUiEnabled,
} from '../../shared/config/admin-feature-flags'
import {
  fetchNotificationAnalyticsSummary,
  type NotificationAnalyticsSummary,
} from '../../features/admin/notification-analytics-api'
import { AdminMetricCard } from '../../features/admin/notifications/AdminMetricCard'

function rangeLast24h(): { from: string; to: string } {
  const to = new Date()
  const from = new Date(to.getTime() - 24 * 60 * 60 * 1000)
  return { from: from.toISOString(), to: to.toISOString() }
}

function rangeLast7d(): { from: string; to: string } {
  const to = new Date()
  const from = new Date(to.getTime() - 7 * 24 * 60 * 60 * 1000)
  return { from: from.toISOString(), to: to.toISOString() }
}

export function AdminNotificationAnalyticsPage() {
  const user = useAuthStore((s) => s.user)
  const allowed = hasPlatformPermission(user, PERM_NOTIFICATIONS_ANALYTICS_READ)
  const showDeadLetterLink =
    isNotificationDeadLetterUiEnabled() && hasPlatformPermission(user, PERM_NOTIFICATIONS_DEAD_LETTER_READ)
  const showRetentionLink =
    isNotificationRetentionUiEnabled() && hasPlatformPermission(user, PERM_NOTIFICATIONS_RETENTION_READ)
  const [preset, setPreset] = useState<'24h' | '7d'>('24h')
  const [data, setData] = useState<NotificationAnalyticsSummary | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const range = useMemo(() => (preset === '24h' ? rangeLast24h() : rangeLast7d()), [preset])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const r = await fetchNotificationAnalyticsSummary({ ...range, bucket: 'hour' })
      setData(r)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to load analytics')
      setData(null)
    } finally {
      setLoading(false)
    }
  }, [range])

  useEffect(() => {
    if (!allowed || !isNotificationAnalyticsUiEnabled()) return
    void load()
  }, [allowed, load])

  if (!isNotificationAnalyticsUiEnabled()) {
    return (
      <div className="space-y-3">
        <PageHeader title="Notification analytics" subtitle="Operational delivery dashboard" />
        <Card className="p-4 text-sm text-slate-600">
          Notification analytics UI is disabled. Set FRONTEND_NOTIFICATION_ANALYTICS_ENABLED in runtime config.
        </Card>
      </div>
    )
  }

  if (!allowed) {
    return (
      <div className="space-y-3">
        <PageHeader title="Notification analytics" subtitle="Operational delivery dashboard" />
        <Card className="p-4 text-sm text-slate-600">You do not have permission to view notification analytics.</Card>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="Notification analytics"
        subtitle="Privacy-safe aggregates only — no message bodies, emails, or per-user metrics."
      />
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs font-semibold uppercase text-slate-500">Range</span>
        <button
          type="button"
          className={`rounded px-2 py-1 text-xs font-medium ${
            preset === '24h' ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-700'
          }`}
          onClick={() => setPreset('24h')}
        >
          Last 24h
        </button>
        <button
          type="button"
          className={`rounded px-2 py-1 text-xs font-medium ${
            preset === '7d' ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-700'
          }`}
          onClick={() => setPreset('7d')}
        >
          Last 7d
        </button>
        <span className="text-xs text-slate-500">See docs/notification-analytics-privacy.md</span>
      </div>
      {error ? <ErrorAlert message={error} /> : null}
      {loading ? <LoadingState label="Loading analytics summary" /> : null}
      {data && !loading ? (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" data-testid="delivery-health-cards">
            <AdminMetricCard title="Created (in-app + email queued)" value={data.totals.created} hint="Aggregate only" />
            <AdminMetricCard title="Sent" value={data.totals.sent} />
            <AdminMetricCard title="Failed" value={data.totals.failed} />
            <AdminMetricCard title="Dead-letter (email)" value={data.totals.dead} />
            <AdminMetricCard title="Skipped (preference)" value={data.totals.skippedPreference} />
            <AdminMetricCard
              title="Digest queued / sent"
              value={data.totals.digestQueued + data.totals.digestSent}
            />
            <AdminMetricCard title="Quiet-hours delayed" value={data.totals.quietHoursDelayed} />
          </div>
          <Card className="space-y-2 p-4">
            <p className="text-xs font-semibold uppercase text-slate-500">Fanout outbox (live)</p>
            <p className="text-sm text-slate-800">
              Pending {data.fanout.pending} · Retrying {data.fanout.retrying} · Dead {data.fanout.dead}
            </p>
            {!data.workers.fanoutWorkerEnabled ? (
              <p className="text-xs text-amber-800">Fanout worker is disabled in configuration.</p>
            ) : null}
            {showDeadLetterLink ? (
              <p className="text-xs">
                <Link className="text-primary-700 underline" to="/app/admin/notifications/dead-letter">
                  View dead-letter queue
                </Link>
              </p>
            ) : null}
            {showRetentionLink && data.fanout.dead > 0 ? (
              <p className="text-xs">
                <Link className="text-primary-700 underline" to="/app/admin/notifications/retention">
                  View retention plan (eligible backlog)
                </Link>
              </p>
            ) : null}
          </Card>
          <Card className="space-y-2 p-4">
            <p className="text-xs font-semibold uppercase text-slate-500">SSE (pod-local connections)</p>
            <p className="text-sm text-slate-800">
              Active {data.sse.activeConnections} · Send failures (range) {data.sse.sendFailuresInRange} · Events sent
              (meter total) {data.sse.eventsSentMeterTotal}
            </p>
          </Card>
          <Card className="space-y-2 p-4">
            <p className="text-xs font-semibold uppercase text-slate-500">Redis fanout (range)</p>
            <p className="text-sm text-slate-800">
              Publish OK {data.redisFanout.publishSuccessInRange} · Fail {data.redisFanout.publishFailureInRange} ·
              Subscriber recv {data.redisFanout.subscriberReceivedInRange}
            </p>
          </Card>
          <Card className="space-y-2 p-4">
            <p className="text-xs font-semibold uppercase text-slate-500">Digest worker</p>
            <p className="text-sm text-slate-800">
              Pending items {data.digest.pendingItems} · Worker {data.digest.workerEnabled ? 'on' : 'off'} · Digest{' '}
              {data.digest.digestEnabled ? 'enabled' : 'disabled'}
            </p>
            {!data.digest.workerEnabled || !data.digest.digestEnabled ? (
              <p className="text-xs text-amber-800">Digest processing may be disabled — check notification-service config.</p>
            ) : null}
          </Card>
          <Card className="space-y-2 p-4">
            <p className="text-xs font-semibold uppercase text-slate-500">Channel breakdown</p>
            <div className="overflow-x-auto text-xs">
              <table className="min-w-full text-left">
                <thead>
                  <tr className="border-b text-slate-500">
                    <th className="py-1 pr-2">Channel</th>
                    <th className="py-1 pr-2">Created</th>
                    <th className="py-1 pr-2">Queued</th>
                    <th className="py-1 pr-2">Sent</th>
                    <th className="py-1 pr-2">Failed</th>
                  </tr>
                </thead>
                <tbody>
                  {data.byChannel.map((r) => (
                    <tr key={r.channel} className="border-b border-slate-100">
                      <td className="py-1 pr-2 font-mono">{r.channel}</td>
                      <td className="py-1 pr-2">{r.created}</td>
                      <td className="py-1 pr-2">{r.queued}</td>
                      <td className="py-1 pr-2">{r.sent}</td>
                      <td className="py-1 pr-2">{r.failed}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
          <Card className="space-y-2 p-4">
            <p className="text-xs font-semibold uppercase text-slate-500">Type breakdown (in-app created)</p>
            <div className="overflow-x-auto text-xs">
              <table className="min-w-full text-left">
                <thead>
                  <tr className="border-b text-slate-500">
                    <th className="py-1 pr-2">Type</th>
                    <th className="py-1 pr-2">Created</th>
                  </tr>
                </thead>
                <tbody>
                  {data.byType.map((r) => (
                    <tr key={r.notificationType} className="border-b border-slate-100">
                      <td className="py-1 pr-2 font-mono">{r.notificationType}</td>
                      <td className="py-1 pr-2">{r.created}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
          <p className="text-xs text-slate-500">
            Runbook: docs/notification-analytics-dashboard.md · Dead-letter and retention tooling are gated by feature
            flags and RBAC (see docs/notification-dead-letter-requeue.md, docs/notification-retention-worker.md).
          </p>
        </>
      ) : null}
    </div>
  )
}


import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_NOTIFICATIONS_ANALYTICS_READ,
  PERM_NOTIFICATIONS_LEGAL_HOLD_READ,
  PERM_NOTIFICATIONS_RETENTION_READ,
  PERM_NOTIFICATIONS_RETENTION_RUN,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import {
  isNotificationAnalyticsUiEnabled,
  isNotificationLegalHoldUiEnabled,
  isNotificationRetentionPurgeUiEnabled,
  isNotificationRetentionUiEnabled,
} from '../../shared/config/admin-feature-flags'
import {
  fetchRetentionPlan,
  runRetention,
  type RetentionPlanResponse,
} from '../../features/admin/notification-retention-api'

export function AdminNotificationRetentionPage() {
  const user = useAuthStore((s) => s.user)
  const canRead = hasPlatformPermission(user, PERM_NOTIFICATIONS_RETENTION_READ)
  const canRun = hasPlatformPermission(user, PERM_NOTIFICATIONS_RETENTION_RUN)
  const showAnalyticsLink =
    isNotificationAnalyticsUiEnabled() && hasPlatformPermission(user, PERM_NOTIFICATIONS_ANALYTICS_READ)
  const showLegalHoldsLink =
    isNotificationLegalHoldUiEnabled() && hasPlatformPermission(user, PERM_NOTIFICATIONS_LEGAL_HOLD_READ)
  const [plan, setPlan] = useState<RetentionPlanResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dryMsg, setDryMsg] = useState<string | null>(null)
  const [purgeOpen, setPurgeOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [confirmText, setConfirmText] = useState('')
  const [purgeBusy, setPurgeBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    setDryMsg(null)
    try {
      const p = await fetchRetentionPlan(true)
      setPlan(p)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to load retention plan')
      setPlan(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!canRead || !isNotificationRetentionUiEnabled()) return
    void load()
  }, [canRead, load])

  const onDryRunPost = async () => {
    setDryMsg(null)
    try {
      await runRetention({ dryRun: true, target: 'ALL', reason: 'Dry-run validation' })
      setDryMsg('Dry-run recorded; plan refreshed.')
      await load()
    } catch (e: unknown) {
      setDryMsg(e instanceof Error ? e.message : 'Dry-run failed')
    }
  }

  const onPurge = async () => {
    if (reason.trim().length < 10) {
      setError('Reason must be at least 10 characters.')
      return
    }
    if (confirmText !== 'DELETE') {
      setError('Type DELETE to confirm irreversible purge.')
      return
    }
    setPurgeBusy(true)
    setError(null)
    try {
      const r = await runRetention({ dryRun: false, target: 'ALL', reason: reason.trim() })
      setPlan(r.planSnapshot)
      setPurgeOpen(false)
      setReason('')
      setConfirmText('')
      const skipped = r.skippedByLegalHold ?? 0
      const keys = r.legalHoldKeysBlocking ?? []
      setDryMsg(
        `Purge completed. Rows deleted (total): ${r.totalDeleted}` +
          (skipped > 0
            ? `. Skipped (eligible under hold): ${skipped}. Holds: ${keys.join(', ') || '—'}.`
            : ''),
      )
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Purge failed')
    } finally {
      setPurgeBusy(false)
    }
  }

  if (!isNotificationRetentionUiEnabled()) {
    return (
      <div className="space-y-3">
        <PageHeader title="Notification retention" subtitle="Purge planning (no raw content)" />
        <Card className="p-4 text-sm text-slate-600">
          Retention UI is disabled. Set FRONTEND_NOTIFICATION_RETENTION_ENABLED in runtime config.
        </Card>
      </div>
    )
  }

  if (!canRead) {
    return (
      <div className="space-y-3">
        <PageHeader title="Notification retention" subtitle="Purge planning (no raw content)" />
        <Card className="p-4 text-sm text-slate-600">You do not have permission to view retention plans.</Card>
      </div>
    )
  }

  const showPurge = canRun && isNotificationRetentionPurgeUiEnabled()

  return (
    <div className="space-y-4">
      <PageHeader
        title="Notification retention"
        subtitle="Aggregate operational data only. Destructive purge is off by default server-side."
      />
      {showAnalyticsLink || showLegalHoldsLink ? (
        <p className="flex flex-wrap gap-3 text-sm text-slate-600">
          {showAnalyticsLink ? (
            <Link to="/app/admin/notifications/analytics" className="text-primary-700 underline">
              Back to notification analytics
            </Link>
          ) : null}
          {showLegalHoldsLink ? (
            <Link to="/app/admin/notifications/legal-holds" className="text-primary-700 underline">
              Legal holds
            </Link>
          ) : null}
        </p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          className="rounded bg-slate-800 px-3 py-1.5 text-sm font-medium text-white"
          onClick={() => void load()}
          disabled={loading}
        >
          Refresh plan
        </button>
        <button
          type="button"
          className="rounded border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
          onClick={() => void onDryRunPost()}
          disabled={loading}
        >
          Record dry-run (audit)
        </button>
        {showPurge ? (
          <button
            type="button"
            className="rounded border border-red-300 bg-red-50 px-3 py-1.5 text-sm font-medium text-red-900"
            onClick={() => setPurgeOpen(true)}
          >
            Manual purge…
          </button>
        ) : null}
      </div>
      {error ? <ErrorAlert message={error} /> : null}
      {dryMsg ? <Card className="p-3 text-sm text-slate-700">{dryMsg}</Card> : null}
      {loading && !plan ? <LoadingState label="Loading retention plan" /> : null}
      {plan ? (
        <>
          {plan.warnings.length > 0 ? (
            <Card className="border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
              <p className="font-semibold">Warnings</p>
              <ul className="mt-1 list-inside list-disc">
                {plan.warnings.map((w) => (
                  <li key={w}>{w}</li>
                ))}
              </ul>
            </Card>
          ) : null}
          <Card className="overflow-x-auto p-0">
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-3 py-2">Target</th>
                  <th className="px-3 py-2">Retention</th>
                  <th className="px-3 py-2">Eligible</th>
                  <th className="px-3 py-2">Cutoff (UTC)</th>
                  <th className="px-3 py-2">Oldest eligible</th>
                  <th className="px-3 py-2">Legal hold</th>
                  <th className="px-3 py-2">Purgeable</th>
                </tr>
              </thead>
              <tbody>
                {plan.targets.map((t) => (
                  <tr key={t.target} className="border-b border-slate-100">
                    <td className="px-3 py-2 font-mono text-xs">{t.target}</td>
                    <td className="px-3 py-2">{t.retention}</td>
                    <td className="px-3 py-2">{t.eligibleCount}</td>
                    <td className="px-3 py-2 text-xs text-slate-600">{t.cutoff}</td>
                    <td className="px-3 py-2 text-xs text-slate-600">{t.oldestEligibleAt ?? '—'}</td>
                    <td className="px-3 py-2 text-xs">
                      {t.blockedByLegalHold
                        ? `Blocked (${(t.activeHoldKeys ?? []).join(', ') || 'hold'})`
                        : '—'}
                    </td>
                    <td className="px-3 py-2">{t.purgeableCount ?? t.eligibleCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
          <Card className="p-4 text-xs text-slate-600">
            <p className="font-semibold text-slate-800">Policies (summary)</p>
            <ul className="mt-2 list-inside list-disc space-y-1">
              <li>Hourly analytics buckets past retention are eligible (derived aggregates only).</li>
              <li>Fanout outbox: only SENT (by sent time) and DEAD (by dead/created time) after retention.</li>
              <li>Active states (PENDING, SENDING, …) are never deleted by this worker.</li>
              <li>Digest PENDING rows are never deleted; terminal digest rows after retention.</li>
              <li>Email rows: terminal statuses only, after updated time passes retention.</li>
            </ul>
          </Card>
        </>
      ) : null}

      {purgeOpen ? (
        <Card className="space-y-3 border-red-200 p-4">
          <p className="text-sm font-semibold text-red-900">Destructive purge</p>
          <p className="text-sm text-slate-700">
            This permanently deletes eligible rows up to the server batch limit. Requires MFA (when enforced) and
            NOTIFICATION_RETENTION_MANUAL_RUN_ENABLED on the notification-service.
          </p>
          <label className="block text-xs font-medium text-slate-600">
            Reason (required, min 10 chars)
            <textarea
              className="mt-1 w-full rounded border border-slate-300 p-2 text-sm"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <label className="block text-xs font-medium text-slate-600">
            Type DELETE to confirm
            <input
              className="mt-1 w-full rounded border border-slate-300 p-2 text-sm"
              value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
            />
          </label>
          <div className="flex gap-2">
            <button
              type="button"
              className="rounded bg-red-700 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              disabled={purgeBusy}
              onClick={() => void onPurge()}
            >
              Execute purge
            </button>
            <button
              type="button"
              className="rounded border border-slate-300 px-3 py-1.5 text-sm"
              disabled={purgeBusy}
              onClick={() => setPurgeOpen(false)}
            >
              Cancel
            </button>
          </div>
        </Card>
      ) : null}
    </div>
  )
}

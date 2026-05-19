import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
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
import { RetentionTargetTable } from '../../features/admin/retention/RetentionTargetTable'
import { PurgeConfirmationDialog } from '../../features/admin/retention/PurgeConfirmationDialog'
import { savePurgeResult } from '../../features/admin/retention/purge-result-storage'
import { maskActorId } from '../../features/admin/notifications/notification-ops-utils'

export function AdminNotificationRetentionPage() {
  const navigate = useNavigate()
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
      const requestId = `purge-${Date.now()}`
      savePurgeResult({
        requestId,
        recordedAt: new Date().toISOString(),
        actorLabel: maskActorId(user?.id),
        result: r,
      })
      navigate('/app/admin/retention/purge-result')
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
          <Link to="/app/admin/retention" className="text-primary-700 underline">
            Retention hub
          </Link>
        </p>
      ) : (
        <p className="text-sm">
          <Link to="/app/admin/retention" className="text-primary-700 underline">
            Retention hub
          </Link>
        </p>
      )}
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
          <RetentionTargetTable targets={plan.targets} />
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

      <PurgeConfirmationDialog
        open={purgeOpen}
        reason={reason}
        confirmText={confirmText}
        busy={purgeBusy}
        purgeEnabled={showPurge}
        mfaRequired
        dryRunReference={plan?.generatedAt}
        onReasonChange={setReason}
        onConfirmTextChange={setConfirmText}
        onConfirm={() => void onPurge()}
        onCancel={() => setPurgeOpen(false)}
      />
    </div>
  )
}

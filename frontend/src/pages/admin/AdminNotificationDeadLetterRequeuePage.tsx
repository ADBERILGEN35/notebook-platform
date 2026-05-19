import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_NOTIFICATIONS_DEAD_LETTER_REQUEUE,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import { isNotificationDeadLetterUiEnabled } from '../../shared/config/admin-feature-flags'
import { DuplicateRiskBadge } from '../../features/admin/notifications/DuplicateRiskBadge'
import { RequeueEligibilityChecklist } from '../../features/admin/notifications/RequeueEligibilityChecklist'
import { OperationalRunbookLink } from '../../features/admin/notifications/OperationalRunbookLink'
import { useDeadLetterEvent } from '../../features/admin/notifications/use-dead-letter-event'
import {
  dryRunDeadLetterRequeue,
  requeueDeadLetter,
  type RequeueDryRunResponse,
} from '../../features/admin/notification-dead-letter-api'

export function AdminNotificationDeadLetterRequeuePage() {
  const { eventId } = useParams<{ eventId: string }>()
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const canRequeue = hasPlatformPermission(user, PERM_NOTIFICATIONS_DEAD_LETTER_REQUEUE)
  const { item, loading, error } = useDeadLetterEvent(eventId)
  const [dryRun, setDryRun] = useState<RequeueDryRunResponse | null>(null)
  const [dryLoading, setDryLoading] = useState(false)
  const [dryError, setDryError] = useState<string | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  const [reason, setReason] = useState('')
  const [idempotencyKey] = useState(() => crypto.randomUUID())
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!eventId || !canRequeue) return
    setDryLoading(true)
    void dryRunDeadLetterRequeue(eventId)
      .then(setDryRun)
      .catch((e) => setDryError(e instanceof Error ? e.message : 'Dry-run failed'))
      .finally(() => setDryLoading(false))
  }, [eventId, canRequeue])

  async function submitRequeue() {
    if (!eventId || !dryRun?.canRequeue) return
    if (reason.trim().length < 5) {
      setSubmitError('Reason must be at least 5 characters.')
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    try {
      await requeueDeadLetter(eventId, { idempotencyKey, reason: reason.trim() })
      navigate(`/app/admin/notifications/dead-letter/${eventId}`)
    } catch (e) {
      setSubmitError(e instanceof Error ? e.message : 'Requeue failed')
    } finally {
      setSubmitting(false)
    }
  }

  if (!isNotificationDeadLetterUiEnabled()) {
    return (
      <div className="space-y-3">
        <PageHeader title="Requeue workflow" subtitle="Dry-run first" />
        <Card className="p-4 text-sm text-slate-600">Dead-letter UI is disabled.</Card>
      </div>
    )
  }

  if (!canRequeue) {
    return (
      <div className="space-y-3">
        <PageHeader title="Requeue workflow" subtitle="Dry-run first" />
        <Card className="p-4 text-sm text-slate-600">You do not have permission to requeue dead-letter events.</Card>
      </div>
    )
  }

  const reasonOk = reason.trim().length >= 5
  const canSubmit = Boolean(dryRun?.canRequeue && confirmed && reasonOk && !submitting)

  return (
    <div className="space-y-4" data-testid="dead-letter-requeue-workflow">
      <PageHeader
        title="Requeue workflow"
        subtitle="Dry-run analysis, eligibility checks, and confirmation. Production safety: MFA may be required."
      />
      <p className="text-sm">
        <Link className="text-primary-700 underline" to={`/app/admin/notifications/dead-letter/${eventId}`}>
          Event details
        </Link>
        {' · '}
        <Link className="text-primary-700 underline" to="/app/admin/notifications/dead-letter">
          Queue
        </Link>
      </p>
      <Card className="border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
        Production safety: requeue may duplicate SSE delivery. Clients must be idempotent. Do not proceed without
        dry-run approval and operational sign-off.
      </Card>
      {error ? <ErrorAlert message={error} /> : null}
      {dryError ? <ErrorAlert message={dryError} /> : null}
      {loading || dryLoading ? <LoadingState label="Loading workflow" /> : null}
      {item ? (
        <Card className="p-3 text-sm text-slate-700">
          Event <span className="font-mono">{item.eventType}</span> · Source {item.source}
        </Card>
      ) : null}
      {dryRun ? (
        <>
          <Card className="space-y-2 p-4">
            <p className="text-sm font-semibold text-slate-900">Dry-run analysis</p>
            <DuplicateRiskBadge risk={dryRun.impact.duplicateRisk} />
            <p className="text-sm text-slate-700">{dryRun.impact.reason}</p>
            <RequeueEligibilityChecklist dryRun={dryRun} />
          </Card>
          {!dryRun.canRequeue ? (
            <Card className="p-3 text-sm text-amber-900">Requeue is not eligible for this event per server checks.</Card>
          ) : (
            <Card className="space-y-3 p-4" data-testid="requeue-confirm-shell">
              <p className="text-sm font-semibold text-slate-900">Final confirmation</p>
              <p className="text-xs text-slate-600">MFA may be required when gateway enforces admin-write MFA.</p>
              <label className="flex flex-col text-xs">
                <span className="font-semibold text-slate-600">Reason (required, min 5 chars)</span>
                <textarea
                  className="mt-1 min-h-[72px] rounded border border-slate-200 p-2 text-sm"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} />
                I reviewed dry-run impact and duplicate risk.
              </label>
              {submitError ? <p className="text-xs text-red-700">{submitError}</p> : null}
              <button
                type="button"
                className="rounded bg-slate-800 px-3 py-1.5 text-sm text-white disabled:opacity-50"
                disabled={!canSubmit}
                onClick={() => void submitRequeue()}
              >
                Submit requeue
              </button>
            </Card>
          )}
        </>
      ) : null}
      <OperationalRunbookLink docPath="docs/notification-dead-letter-requeue.md" label="Requeue runbook" />
    </div>
  )
}

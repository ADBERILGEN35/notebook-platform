import { Link, useParams } from 'react-router-dom'
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
import { DuplicateRiskBadge } from '../../features/admin/notifications/DuplicateRiskBadge'
import { OperationalRunbookLink } from '../../features/admin/notifications/OperationalRunbookLink'
import {
  sanitizeDeadLetterForDisplay,
  maskRecipientHash,
} from '../../features/admin/notifications/notification-ops-utils'
import { useDeadLetterEvent } from '../../features/admin/notifications/use-dead-letter-event'
import { dryRunDeadLetterRequeue } from '../../features/admin/notification-dead-letter-api'
import { useEffect, useState } from 'react'
import type { RequeueDryRunResponse } from '../../features/admin/notification-dead-letter-api'

export function AdminNotificationDeadLetterDetailPage() {
  const { eventId } = useParams<{ eventId: string }>()
  const user = useAuthStore((s) => s.user)
  const canRead = hasPlatformPermission(user, PERM_NOTIFICATIONS_DEAD_LETTER_READ)
  const canRequeue = hasPlatformPermission(user, PERM_NOTIFICATIONS_DEAD_LETTER_REQUEUE)
  const { item, loading, error } = useDeadLetterEvent(eventId)
  const [dryRun, setDryRun] = useState<RequeueDryRunResponse | null>(null)
  const [dryError, setDryError] = useState<string | null>(null)

  useEffect(() => {
    if (!eventId || !canRead) return
    void (async () => {
      try {
        const r = await dryRunDeadLetterRequeue(eventId)
        setDryRun(r)
        setDryError(null)
      } catch (e) {
        setDryError(e instanceof Error ? e.message : 'Dry-run unavailable')
      }
    })()
  }, [eventId, canRead])

  if (!isNotificationDeadLetterUiEnabled()) {
    return (
      <div className="space-y-3">
        <PageHeader title="Dead-letter event" subtitle="Sanitized metadata" />
        <Card className="p-4 text-sm text-slate-600">Dead-letter UI is disabled.</Card>
      </div>
    )
  }

  if (!canRead) {
    return (
      <div className="space-y-3">
        <PageHeader title="Dead-letter event" subtitle="Sanitized metadata" />
        <Card className="p-4 text-sm text-slate-600">You do not have permission to view dead-letter records.</Card>
      </div>
    )
  }

  const meta = item ? sanitizeDeadLetterForDisplay(item) : null

  return (
    <div className="space-y-4" data-testid="dead-letter-event-detail">
      <PageHeader
        title="Dead-letter event"
        subtitle="Sanitized metadata only — no notification body or recipient PII."
      />
      <p className="text-sm">
        <Link className="text-primary-700 underline" to="/app/admin/notifications/dead-letter">
          Back to queue
        </Link>
        {canRequeue && eventId ? (
          <>
            {' · '}
            <Link
              className="text-primary-700 underline"
              to={`/app/admin/notifications/dead-letter/${eventId}/requeue`}
            >
              Requeue workflow
            </Link>
          </>
        ) : null}
      </p>
      {error ? <ErrorAlert message={error} /> : null}
      {dryError ? <Card className="p-3 text-sm text-amber-800">{dryError}</Card> : null}
      {loading ? <LoadingState label="Loading event" /> : null}
      {item && meta ? (
        <>
          <Card className="p-4">
            <pre className="overflow-x-auto text-xs text-slate-800">{JSON.stringify(meta, null, 2)}</pre>
          </Card>
          <Card className="space-y-2 p-4">
            <p className="text-xs font-semibold uppercase text-slate-500">Recipient (masked hash)</p>
            <p className="font-mono text-sm">{maskRecipientHash(item.recipientUserIdHash)}</p>
          </Card>
          <Card className="space-y-2 p-4">
            <p className="text-xs font-semibold uppercase text-slate-500">Attempt timeline</p>
            <ul className="list-inside list-disc text-sm text-slate-700">
              <li>Created: {item.createdAt}</li>
              <li>Updated: {item.updatedAt}</li>
              <li>Dead: {item.deadAt ?? '—'}</li>
              <li>Attempts: {item.attemptCount}</li>
              <li>Requeues: {item.requeueCount}</li>
            </ul>
          </Card>
          <Card className="space-y-2 p-4">
            <p className="text-xs font-semibold uppercase text-slate-500">Failure summary</p>
            <p className="font-mono text-sm text-slate-800">{item.lastErrorCode}</p>
            <p className="text-sm text-slate-700">{item.lastErrorSummary}</p>
          </Card>
          {dryRun ? (
            <Card className="space-y-2 p-4">
              <p className="text-xs font-semibold uppercase text-slate-500">Eligibility (dry-run)</p>
              <p className="text-sm">
                Can requeue: <strong>{dryRun.canRequeue ? 'yes' : 'no'}</strong>
              </p>
              <DuplicateRiskBadge risk={dryRun.impact.duplicateRisk} />
              <p className="text-sm text-slate-700">{dryRun.impact.reason}</p>
            </Card>
          ) : null}
          <OperationalRunbookLink docPath="docs/notification-dead-letter-requeue.md" />
        </>
      ) : null}
    </div>
  )
}

import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_BREAK_GLASS_READ,
  PERM_BREAK_GLASS_REVOKE,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import {
  getBreakGlassEvent,
  listBreakGlassEvents,
  reviewBreakGlassEvent,
  revokeBreakGlassEventToken,
} from '../../features/admin/breakglass/admin-break-glass-api'
import { listBreakGlassActiveSessions } from '../../features/admin/breakglass/admin-break-glass-sessions-api'
import { BreakGlassActiveSessionsPanel } from '../../features/admin/breakglass/BreakGlassActiveSessionsPanel'
import { useEnterpriseStatus } from '../../features/admin/enterprise/use-enterprise-status'
import { AdminPageShell } from '../../features/admin/shared/AdminPageShell'
import { BreakGlassStatusCard } from '../../features/admin/security/BreakGlassStatusCard'
import { BreakGlassRevocationSummary } from '../../features/admin/security/BreakGlassRevocationSummary'
import { maskSessionId } from '../../features/admin/security/mask-session-id'
import { AdminRunbookLink } from '../../features/admin/shared/AdminRunbookLink'
import {
  getAuditApiMode,
  isAdminUiDevOpen,
  isBreakGlassRevocationUiEnabled,
  isBreakGlassReviewUiEnabled,
  isBreakGlassRotationUiEnabled,
} from '../../shared/config/admin-feature-flags'
import { PermissionDenied } from '../../shared/components/PermissionDenied'
import { ErrorAlert } from '../../shared/components/ErrorAlert'

export function AdminBreakGlassOpsPage() {
  const user = useAuthStore((s) => s.user)
  const devOpen = isAdminUiDevOpen() && getAuditApiMode() === 'mock'
  const reviewOn = isBreakGlassReviewUiEnabled()
  const canRead = devOpen || hasPlatformPermission(user, PERM_BREAK_GLASS_READ)
  const canRevoke = isBreakGlassRevocationUiEnabled() && hasPlatformPermission(user, PERM_BREAK_GLASS_REVOKE)

  const statusQ = useEnterpriseStatus()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT' | 'CLOSE'>('APPROVE')
  const [reason, setReason] = useState('')
  const qc = useQueryClient()

  const sessionsQ = useQuery({
    queryKey: ['break-glass-active-sessions'],
    queryFn: listBreakGlassActiveSessions,
    enabled: canRead && isBreakGlassRevocationUiEnabled(),
  })

  const list = useQuery({
    queryKey: ['break-glass-events'],
    queryFn: () => listBreakGlassEvents({ page: 0, size: 50 }),
    enabled: canRead && reviewOn,
  })
  const detail = useQuery({
    queryKey: ['break-glass-event', selectedId],
    queryFn: () => getBreakGlassEvent(selectedId as string),
    enabled: !!selectedId && reviewOn,
  })

  const reviewMutation = useMutation({
    mutationFn: () =>
      reviewBreakGlassEvent(selectedId as string, {
        decision,
        reason,
        rotationRunbookAcknowledged: true,
      }),
    onSuccess: async () => {
      setReason('')
      await qc.invalidateQueries({ queryKey: ['break-glass-events'] })
      await qc.invalidateQueries({ queryKey: ['break-glass-event', selectedId] })
    },
  })
  const revokeMutation = useMutation({
    mutationFn: (revokeReason: string) =>
      revokeBreakGlassEventToken(selectedId as string, { reason: revokeReason }),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['break-glass-events'] })
      await qc.invalidateQueries({ queryKey: ['break-glass-event', selectedId] })
    },
  })

  const overdue = useMemo(() => list.data?.overdueCount ?? 0, [list.data?.overdueCount])

  if (!reviewOn && !isBreakGlassRevocationUiEnabled()) {
    return (
      <AdminPageShell title="Break-glass operations" subtitle="Feature flags disabled in this environment.">
        <p className="text-sm text-slate-600">
          Enable BREAK_GLASS_REVIEW_UI_ENABLED or BREAK_GLASS_REVOCATION_UI_ENABLED for non-production diagnostics.
        </p>
      </AdminPageShell>
    )
  }

  if (!canRead) {
    return <PermissionDenied title="Break-glass" message="admin:break-glass:read permission required." />
  }

  return (
    <AdminPageShell
      title="Break-glass operations"
      subtitle="Status, governance events, and session revocation. No tokens, JWTs, or emergency credentials are displayed."
    >
      <BreakGlassStatusCard status={statusQ.data} />
      <BreakGlassRevocationSummary
        activeCount={sessionsQ.data?.activeCount ?? null}
        revocationUiEnabled={isBreakGlassRevocationUiEnabled()}
      />

      {isBreakGlassRotationUiEnabled() ? (
        <p className="text-sm">
          <Link className="text-primary-600 hover:underline" to="/app/admin/security/break-glass/rotation">
            Token rotation governance
          </Link>
        </p>
      ) : null}

      <BreakGlassActiveSessionsPanel />

      {reviewOn ? (
        <section className="space-y-3 border-t border-slate-200 pt-4">
          <h3 className="text-sm font-semibold text-slate-900">Governance events</h3>
          <p className="text-xs text-amber-700">Pending overdue reviews: {overdue}</p>

          <div className="overflow-x-auto rounded border border-slate-200 bg-white">
            <table className="min-w-full text-sm" data-testid="break-glass-events-table">
              <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-3 py-2">Issued</th>
                  <th className="px-3 py-2">Mode</th>
                  <th className="px-3 py-2">Status</th>
                  <th className="px-3 py-2">Token status</th>
                  <th className="px-3 py-2">Session (masked)</th>
                </tr>
              </thead>
              <tbody>
                {(list.data?.items ?? []).map((row) => (
                  <tr
                    key={row.id}
                    className="cursor-pointer border-t border-slate-100 hover:bg-slate-50"
                    onClick={() => setSelectedId(row.id)}
                  >
                    <td className="px-3 py-2">{row.issuedAt}</td>
                    <td className="px-3 py-2">{row.mode}</td>
                    <td className="px-3 py-2">{row.status}</td>
                    <td className="px-3 py-2">{row.tokenStatus}</td>
                    <td className="px-3 py-2 font-mono text-xs">{maskSessionId(row.sessionId)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {list.isError ? <ErrorAlert error={list.error} /> : null}

          {detail.data ? (
            <div className="rounded border border-slate-200 bg-slate-50 p-3 space-y-2">
              <p className="text-sm font-medium text-slate-900">Review event</p>
              <p className="text-xs text-slate-600">Token status: {detail.data.tokenStatus} (no token material shown)</p>
              <div className="flex flex-wrap gap-2">
                <select
                  className="rounded border border-slate-300 px-2 py-1 text-sm"
                  value={decision}
                  onChange={(e) => setDecision(e.target.value as 'APPROVE' | 'REJECT' | 'CLOSE')}
                >
                  <option value="APPROVE">APPROVE</option>
                  <option value="REJECT">REJECT</option>
                  <option value="CLOSE">CLOSE</option>
                </select>
                <input
                  className="min-w-[12rem] flex-1 rounded border border-slate-300 px-2 py-1 text-sm"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="Review reason (min 10 chars)"
                />
                <button
                  type="button"
                  className="rounded bg-primary-700 px-3 py-1.5 text-white disabled:opacity-50"
                  disabled={reason.trim().length < 10 || reviewMutation.isPending}
                  onClick={() => reviewMutation.mutate()}
                >
                  Submit
                </button>
                <button
                  type="button"
                  className="rounded bg-rose-700 px-3 py-1.5 text-white disabled:opacity-50"
                  disabled={
                    !canRevoke || detail.data.tokenStatus !== 'ACTIVE' || revokeMutation.isPending || reason.trim().length < 10
                  }
                  onClick={() => revokeMutation.mutate(reason)}
                >
                  Revoke active token
                </button>
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

      <AdminRunbookLink docPath="docs/break-glass-admin-access.md" label="Break-glass runbook" />
    </AdminPageShell>
  )
}

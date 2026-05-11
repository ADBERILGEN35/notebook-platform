import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_BREAK_GLASS_REVOKE,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import { isBreakGlassRevocationUiEnabled } from '../../shared/config/admin-feature-flags'
import {
  getBreakGlassEvent,
  listBreakGlassEvents,
  reviewBreakGlassEvent,
  revokeBreakGlassEventToken,
} from '../../features/admin/breakglass/admin-break-glass-api'

export function AdminBreakGlassPage() {
  const user = useAuthStore((s) => s.user)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT' | 'CLOSE'>('APPROVE')
  const [reason, setReason] = useState('')
  const qc = useQueryClient()

  const list = useQuery({ queryKey: ['break-glass-events'], queryFn: () => listBreakGlassEvents({ page: 0, size: 50 }) })
  const detail = useQuery({
    queryKey: ['break-glass-event', selectedId],
    queryFn: () => getBreakGlassEvent(selectedId as string),
    enabled: !!selectedId,
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
  const canRevoke = isBreakGlassRevocationUiEnabled() && hasPlatformPermission(user, PERM_BREAK_GLASS_REVOKE)

  return (
    <section className="space-y-4">
      <header>
        <h2 className="text-lg font-semibold text-slate-900">Break-glass events</h2>
        <p className="text-sm text-slate-600">
          Governance review view. No token/assertion/credential material is displayed.
        </p>
        <p className="mt-1 text-xs text-amber-700">Pending overdue reviews: {overdue}</p>
      </header>

      <div className="overflow-x-auto rounded border border-slate-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
            <tr>
              <th className="px-3 py-2">Issued</th>
              <th className="px-3 py-2">Mode</th>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2">Token</th>
              <th className="px-3 py-2">Rotation</th>
              <th className="px-3 py-2">Session</th>
            </tr>
          </thead>
          <tbody>
            {(list.data?.items ?? []).map((row) => (
              <tr key={row.id} className="cursor-pointer border-t border-slate-100 hover:bg-slate-50" onClick={() => setSelectedId(row.id)}>
                <td className="px-3 py-2">{row.issuedAt}</td>
                <td className="px-3 py-2">{row.mode}</td>
                <td className="px-3 py-2">{row.status}</td>
                <td className="px-3 py-2">{row.tokenStatus}</td>
                <td className="px-3 py-2">{row.rotationRequired ? 'required' : 'no'}</td>
                <td className="px-3 py-2">{row.sessionId}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {detail.data ? (
        <div className="rounded border border-slate-200 bg-slate-50 p-3 space-y-2">
          <p className="text-sm font-medium text-slate-900">Review event</p>
          <p className="text-xs text-slate-600">Reviewing does not extend or revoke the already issued token.</p>
          <p className="text-xs text-amber-700">
            Rejecting review may revoke the active token when revoke-on-reject is enabled.
          </p>
          <p className="text-xs text-slate-700">Token status: {detail.data.tokenStatus}</p>
          <div className="flex gap-2">
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
              className="flex-1 rounded border border-slate-300 px-2 py-1 text-sm"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Review reason (min 10 chars)"
            />
            <button
              className="rounded bg-primary-700 px-3 py-1.5 text-white disabled:opacity-50"
              disabled={reason.trim().length < 10 || reviewMutation.isPending}
              onClick={() => reviewMutation.mutate()}
            >
              Submit
            </button>
            <button
              className="rounded bg-rose-700 px-3 py-1.5 text-white disabled:opacity-50"
              disabled={!canRevoke || detail.data.tokenStatus !== 'ACTIVE' || revokeMutation.isPending || reason.trim().length < 10}
              onClick={() => revokeMutation.mutate(reason)}
            >
              Revoke active token
            </button>
          </div>
        </div>
      ) : null}
    </section>
  )
}

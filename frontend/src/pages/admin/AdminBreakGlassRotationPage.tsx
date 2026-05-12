import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_BREAK_GLASS_ROTATION_MANAGE,
  PERM_BREAK_GLASS_ROTATION_READ,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import { isBreakGlassRotationUiEnabled } from '../../shared/config/admin-feature-flags'
import {
  acknowledgeBreakGlassRotationEvent,
  closeBreakGlassRotationEvent,
  getBreakGlassRotationEvent,
  listBreakGlassRotationEvents,
  verifyBreakGlassRotationEvent,
} from '../../features/admin/breakglass/admin-break-glass-rotation-api'

type ActionKind = 'acknowledge' | 'verify' | 'close'

export function AdminBreakGlassRotationPage() {
  const user = useAuthStore((s) => s.user)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [action, setAction] = useState<ActionKind>('acknowledge')
  const [reason, setReason] = useState('')
  const qc = useQueryClient()

  const canRead = hasPlatformPermission(user, PERM_BREAK_GLASS_ROTATION_READ)
  const canManage = hasPlatformPermission(user, PERM_BREAK_GLASS_ROTATION_MANAGE)
  const uiEnabled = isBreakGlassRotationUiEnabled()

  const list = useQuery({
    queryKey: ['break-glass-rotation-events'],
    queryFn: () => listBreakGlassRotationEvents({ page: 0, size: 50 }),
    enabled: uiEnabled && canRead,
  })

  const detail = useQuery({
    queryKey: ['break-glass-rotation-event', selectedId],
    queryFn: () => getBreakGlassRotationEvent(selectedId as string),
    enabled: !!selectedId && uiEnabled && canRead,
  })

  const ackMutation = useMutation({
    mutationFn: () => acknowledgeBreakGlassRotationEvent(selectedId as string, { reason }),
    onSuccess: () => onActionSuccess(),
  })
  const verifyMutation = useMutation({
    mutationFn: () => verifyBreakGlassRotationEvent(selectedId as string, { reason }),
    onSuccess: () => onActionSuccess(),
  })
  const closeMutation = useMutation({
    mutationFn: () => closeBreakGlassRotationEvent(selectedId as string, { reason }),
    onSuccess: () => onActionSuccess(),
  })

  async function onActionSuccess() {
    setReason('')
    await qc.invalidateQueries({ queryKey: ['break-glass-rotation-events'] })
    await qc.invalidateQueries({ queryKey: ['break-glass-rotation-event', selectedId] })
  }

  const requiredCount = list.data?.requiredCount ?? 0
  const openCount = list.data?.openCount ?? 0
  const oldestRequiredAt = useMemo(
    () =>
      (list.data?.items ?? []).find((row) => row.status === 'REQUIRED')?.requiredAt ?? null,
    [list.data?.items],
  )

  const status = detail.data?.status
  const canRunAck = canManage && status === 'REQUIRED'
  const canRunVerify = canManage && (status === 'REQUIRED' || status === 'ACKNOWLEDGED')
  const canRunClose = canManage && status === 'VERIFIED'
  const submitting = ackMutation.isPending || verifyMutation.isPending || closeMutation.isPending
  const submitDisabled = submitting || reason.trim().length < 10 ||
    (action === 'acknowledge' && !canRunAck) ||
    (action === 'verify' && !canRunVerify) ||
    (action === 'close' && !canRunClose)

  if (!uiEnabled) {
    return (
      <section className="space-y-2">
        <h2 className="text-lg font-semibold text-slate-900">Break-glass token rotation</h2>
        <p className="text-sm text-slate-600">
          Rotation governance UI is disabled. Set BREAK_GLASS_ROTATION_UI_ENABLED to enable.
        </p>
      </section>
    )
  }
  if (!canRead) {
    return (
      <section className="space-y-2">
        <h2 className="text-lg font-semibold text-slate-900">Break-glass token rotation</h2>
        <p className="text-sm text-rose-700">
          You do not have the admin:break-glass:rotation:read permission.
        </p>
      </section>
    )
  }

  return (
    <section className="space-y-4">
      <header>
        <h2 className="text-lg font-semibold text-slate-900">Break-glass token rotation</h2>
        <p className="text-sm text-slate-600">
          Track and verify rotation of the configured break-glass static-token hash. The token
          value is never shown. Provision new material out-of-band via External Secret / GitOps,
          then verify here.
        </p>
      </header>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
        <div className="rounded border border-slate-200 bg-white p-3">
          <p className="text-xs uppercase text-slate-500">Rotation required</p>
          <p className="text-lg font-semibold text-rose-700">{requiredCount}</p>
        </div>
        <div className="rounded border border-slate-200 bg-white p-3">
          <p className="text-xs uppercase text-slate-500">Open events</p>
          <p className="text-lg font-semibold text-slate-900">{openCount}</p>
        </div>
        <div className="rounded border border-slate-200 bg-white p-3">
          <p className="text-xs uppercase text-slate-500">Oldest required</p>
          <p className="text-sm text-slate-800">{oldestRequiredAt ?? '—'}</p>
        </div>
      </div>

      <p className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded px-3 py-2">
        The token value is never shown. Update the External Secret / runbook first, then verify.
      </p>

      <div className="overflow-x-auto rounded border border-slate-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
            <tr>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2">Required at</th>
              <th className="px-3 py-2">Acknowledged</th>
              <th className="px-3 py-2">Verified</th>
              <th className="px-3 py-2">Old fp</th>
              <th className="px-3 py-2">New fp</th>
              <th className="px-3 py-2">Trigger</th>
            </tr>
          </thead>
          <tbody>
            {(list.data?.items ?? []).map((row) => (
              <tr
                key={row.id}
                className={
                  'cursor-pointer border-t border-slate-100 hover:bg-slate-50 ' +
                  (selectedId === row.id ? 'bg-slate-50' : '')
                }
                onClick={() => setSelectedId(row.id)}
              >
                <td className="px-3 py-2">{row.status}</td>
                <td className="px-3 py-2">{row.requiredAt}</td>
                <td className="px-3 py-2">{row.acknowledgedAt ?? '—'}</td>
                <td className="px-3 py-2">{row.verifiedAt ?? '—'}</td>
                <td className="px-3 py-2 font-mono text-xs">{row.oldFingerprint ?? '—'}</td>
                <td className="px-3 py-2 font-mono text-xs">{row.newFingerprint ?? '—'}</td>
                <td className="px-3 py-2 text-xs">
                  {row.triggeredBySessionId ?? row.triggeredByEventId ?? '—'}
                </td>
              </tr>
            ))}
            {(list.data?.items ?? []).length === 0 ? (
              <tr>
                <td className="px-3 py-4 text-center text-slate-500" colSpan={7}>
                  No rotation events.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      {detail.data ? (
        <div className="rounded border border-slate-200 bg-slate-50 p-3 space-y-2">
          <p className="text-sm font-medium text-slate-900">Rotation event {detail.data.id}</p>
          <p className="text-xs text-slate-600">Status: {detail.data.status}</p>
          <p className="text-xs text-slate-600">
            Old fingerprint: <span className="font-mono">{detail.data.oldFingerprint ?? '—'}</span>
            {' / '}New fingerprint:{' '}
            <span className="font-mono">{detail.data.newFingerprint ?? '—'}</span>
          </p>
          <p className="text-xs text-slate-600">
            Close is only permitted after verification (External Secret hash changed).
          </p>
          <div className="flex flex-wrap gap-2">
            <select
              className="rounded border border-slate-300 px-2 py-1 text-sm"
              value={action}
              onChange={(e) => setAction(e.target.value as ActionKind)}
            >
              <option value="acknowledge" disabled={!canRunAck}>
                Acknowledge
              </option>
              <option value="verify" disabled={!canRunVerify}>
                Verify
              </option>
              <option value="close" disabled={!canRunClose}>
                Close
              </option>
            </select>
            <input
              className="flex-1 rounded border border-slate-300 px-2 py-1 text-sm"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Reason (min 10 chars)"
            />
            <button
              className="rounded bg-primary-700 px-3 py-1.5 text-white disabled:opacity-50"
              disabled={submitDisabled}
              onClick={() => {
                if (action === 'acknowledge') ackMutation.mutate()
                else if (action === 'verify') verifyMutation.mutate()
                else closeMutation.mutate()
              }}
            >
              Submit
            </button>
          </div>
          {!canManage ? (
            <p className="text-xs text-rose-700">
              You do not have the admin:break-glass:rotation:manage permission. Actions are
              disabled.
            </p>
          ) : null}
        </div>
      ) : null}
    </section>
  )
}

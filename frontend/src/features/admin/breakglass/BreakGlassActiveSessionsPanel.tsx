import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useAuthStore } from '../../auth/auth-store'
import { hasPlatformPermission, PERM_BREAK_GLASS_READ, PERM_BREAK_GLASS_REVOKE } from '../access/admin-permissions'
import { isBreakGlassRevocationUiEnabled } from '../../../shared/config/admin-feature-flags'
import { Button } from '../../../shared/ui/Button'
import { ErrorAlert } from '../../../shared/ui/ErrorAlert'
import {
  listBreakGlassActiveSessions,
  revokeAllBreakGlassActiveSessions,
  revokeBreakGlassSession,
} from './admin-break-glass-sessions-api'

export function BreakGlassActiveSessionsPanel() {
  const user = useAuthStore((s) => s.user)
  const qc = useQueryClient()
  const [reason, setReason] = useState('')
  const [revokeTarget, setRevokeTarget] = useState<string | null>(null)
  const [showRevokeAll, setShowRevokeAll] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const enabled = isBreakGlassRevocationUiEnabled()
  const canRead = hasPlatformPermission(user, PERM_BREAK_GLASS_READ)
  const canRevoke = hasPlatformPermission(user, PERM_BREAK_GLASS_REVOKE)

  const sessionsQuery = useQuery({
    queryKey: ['break-glass-active-sessions'],
    queryFn: listBreakGlassActiveSessions,
    enabled: enabled && canRead,
  })

  const revokeMutation = useMutation({
    mutationFn: (payload: { revokeRef: string; reason: string }) =>
      revokeBreakGlassSession(payload.revokeRef, { reason: payload.reason }),
    onSuccess: (data) => {
      setMessage(
        data.revoked
          ? 'Session revoked.'
          : data.alreadyRevoked
            ? 'Session was already revoked.'
            : data.alreadyExpired
              ? 'Session was already expired.'
              : 'No change.',
      )
      setRevokeTarget(null)
      setReason('')
      void qc.invalidateQueries({ queryKey: ['break-glass-active-sessions'] })
    },
  })

  const revokeAllMutation = useMutation({
    mutationFn: (revokeReason: string) => revokeAllBreakGlassActiveSessions({ reason: revokeReason }),
    onSuccess: (data) => {
      setMessage(`Revoked ${data.revokedCount} active session(s).`)
      setShowRevokeAll(false)
      setReason('')
      void qc.invalidateQueries({ queryKey: ['break-glass-active-sessions'] })
    },
  })

  if (!enabled) {
    return (
      <p className="text-xs text-slate-600">
        Break-glass session revocation UI is disabled (feature flag).
      </p>
    )
  }

  if (!canRead) {
    return <p className="text-xs text-slate-600">Missing permission: break-glass read.</p>
  }

  const mfaRequired =
    revokeMutation.error &&
    typeof revokeMutation.error === 'object' &&
    'code' in revokeMutation.error &&
    (revokeMutation.error as { code?: string }).code === 'ADMIN_WRITE_MFA_REQUIRED'

  return (
    <div className="mt-4 space-y-3 border-t border-slate-200 pt-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 className="text-sm font-semibold text-slate-900">Active break-glass sessions</h4>
        {canRevoke ? (
          <Button
            type="button"
            className="bg-rose-700 text-white hover:bg-rose-800"
            disabled={revokeAllMutation.isPending}
            onClick={() => {
              setShowRevokeAll(true)
              setRevokeTarget(null)
            }}
          >
            Revoke all active
          </Button>
        ) : null}
      </div>

      <p className="text-xs text-slate-600">
        No access tokens or JWT values are shown. Revocation requires a reason (min 10 characters) and
        admin MFA when enforced.
      </p>

      {sessionsQuery.isLoading ? <p className="text-sm text-slate-600">Loading sessions…</p> : null}
      {sessionsQuery.isError ? <ErrorAlert error={sessionsQuery.error} /> : null}

      {sessionsQuery.data ? (
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-200 text-slate-600">
                <th className="py-1 pr-3">Session</th>
                <th className="py-1 pr-3">JTI (masked)</th>
                <th className="py-1 pr-3">Mode</th>
                <th className="py-1 pr-3">Status</th>
                <th className="py-1 pr-3">Issued</th>
                <th className="py-1 pr-3">Expires</th>
                <th className="py-1 pr-3">Token</th>
                {canRevoke ? <th className="py-1">Action</th> : null}
              </tr>
            </thead>
            <tbody>
              {sessionsQuery.data.items.length === 0 ? (
                <tr>
                  <td colSpan={canRevoke ? 8 : 7} className="py-2 text-slate-500">
                    No sessions in retention window.
                  </td>
                </tr>
              ) : (
                sessionsQuery.data.items.map((row) => (
                  <tr key={row.eventId} className="border-b border-slate-100">
                    <td className="py-1 pr-3 font-mono">{row.sessionId}</td>
                    <td className="py-1 pr-3 font-mono">{row.jtiMasked || '—'}</td>
                    <td className="py-1 pr-3">{row.mode}</td>
                    <td className="py-1 pr-3">{row.status}</td>
                    <td className="py-1 pr-3">{row.issuedAt}</td>
                    <td className="py-1 pr-3">{row.expiresAt}</td>
                    <td className="py-1 pr-3">{row.tokenStatus}</td>
                    {canRevoke ? (
                      <td className="py-1">
                        <Button
                          type="button"
                          className="text-xs"
                          disabled={row.tokenStatus !== 'ACTIVE' || revokeMutation.isPending}
                          onClick={() => {
                            setRevokeTarget(row.revokeRef)
                            setShowRevokeAll(false)
                          }}
                        >
                          Revoke
                        </Button>
                      </td>
                    ) : null}
                  </tr>
                ))
              )}
            </tbody>
          </table>
          <p className="mt-1 text-xs text-slate-500">Active count: {sessionsQuery.data.activeCount}</p>
        </div>
      ) : null}

      {(revokeTarget || showRevokeAll) && canRevoke ? (
        <div className="rounded-md border border-amber-200 bg-amber-50 p-3">
          <p className="text-sm font-medium text-amber-950">
            {showRevokeAll ? 'Revoke all active sessions' : 'Revoke session'}
          </p>
          <label className="mt-2 block text-xs text-slate-700">
            Reason (required, min 10 characters)
            <textarea
              className="mt-1 w-full rounded border border-slate-300 p-2 text-sm"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <div className="mt-2 flex gap-2">
            <Button
              type="button"
              className="bg-rose-700 text-white hover:bg-rose-800"
              disabled={
                reason.trim().length < 10 ||
                revokeMutation.isPending ||
                revokeAllMutation.isPending
              }
              onClick={() => {
                if (showRevokeAll) {
                  revokeAllMutation.mutate(reason)
                } else if (revokeTarget) {
                  revokeMutation.mutate({ revokeRef: revokeTarget, reason })
                }
              }}
            >
              Confirm revoke
            </Button>
            <Button
              type="button"
              onClick={() => {
                setRevokeTarget(null)
                setShowRevokeAll(false)
                setReason('')
              }}
            >
              Cancel
            </Button>
          </div>
          {mfaRequired ? (
            <p className="mt-2 text-xs text-rose-800">Admin MFA verification is required for this action.</p>
          ) : null}
          {revokeMutation.isError ? <ErrorAlert error={revokeMutation.error} /> : null}
          {revokeAllMutation.isError ? <ErrorAlert error={revokeAllMutation.error} /> : null}
        </div>
      ) : null}

      {message ? <p className="text-sm text-emerald-800">{message}</p> : null}
    </div>
  )
}
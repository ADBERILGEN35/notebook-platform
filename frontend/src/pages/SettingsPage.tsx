import { useMutation } from '@tanstack/react-query'
import { useNavigate, Link } from 'react-router-dom'
import { logout, revokeAll } from '../features/auth/auth-api'
import { useAuthStore } from '../features/auth/auth-store'
import { Button } from '../shared/components/Button'
import { Card } from '../shared/components/Card'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { PageHeader } from '../shared/components/PageHeader'
import { canShowAdminNavigation } from '../features/admin/access/admin-access'

export function SettingsPage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const clearSession = useAuthStore((state) => state.clearSession)
  const refreshToken = useAuthStore((state) => state.refreshToken)

  const logoutMutation = useMutation({
    mutationFn: () => logout(refreshToken),
    onSettled: () => {
      clearSession()
      navigate('/login')
    },
  })
  const revokeMutation = useMutation({
    mutationFn: revokeAll,
  })

  return (
    <div className="space-y-3">
      <PageHeader title="Settings / Security" subtitle="Session and token controls" />
      <Card>
        <p className="mb-3 text-sm text-slate-600">
          Bearer mode may keep tokens in localStorage for local development; cookie mode uses HttpOnly
          cookies and CSRF headers (see docs/auth-cookie-csrf.md).
        </p>
        <div className="flex gap-2">
          <Button onClick={() => logoutMutation.mutate()}>
            Logout
          </Button>
          <Button className="bg-rose-600 text-white hover:bg-rose-700" onClick={() => revokeMutation.mutate()}>
            Revoke all sessions
          </Button>
        </div>
        {revokeMutation.isSuccess ? (
          <p className="mt-2 text-sm text-slate-600">Revoked: {revokeMutation.data.revokedCount}</p>
        ) : null}
        {revokeMutation.isError ? <ErrorAlert error={revokeMutation.error} /> : null}
      </Card>
      {canShowAdminNavigation(user) ? (
        <Card>
          <p className="text-sm font-medium text-slate-800">Admin</p>
          <p className="mt-1 text-sm text-slate-600">
            Audit and operational tools (mock-backed until platform admin proxy ships).
          </p>
          <Link className="mt-2 inline-block text-sm text-primary-600 hover:underline" to="/app/admin">
            Open admin console
          </Link>
        </Card>
      ) : null}
      <Card>
        <p className="text-sm text-slate-600">Invitations and member management: MVP placeholder.</p>
      </Card>
    </div>
  )
}


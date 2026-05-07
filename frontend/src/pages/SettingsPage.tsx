import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { logout, revokeAll } from '../features/auth/auth-api'
import { useAuthStore } from '../features/auth/auth-store'
import { Button } from '../shared/components/Button'
import { Card } from '../shared/components/Card'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { PageHeader } from '../shared/components/PageHeader'

export function SettingsPage() {
  const navigate = useNavigate()
  const clearSession = useAuthStore((state) => state.clearSession)
  const refreshToken = useAuthStore((state) => state.refreshToken)

  const logoutMutation = useMutation({
    mutationFn: () => logout(refreshToken!),
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
          MVP stores tokens in localStorage. Production should prefer HttpOnly secure cookies.
        </p>
        <div className="flex gap-2">
          <Button onClick={() => logoutMutation.mutate()} disabled={!refreshToken}>
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
      <Card>
        <p className="text-sm text-slate-600">Invitations and member management: MVP placeholder.</p>
      </Card>
    </div>
  )
}


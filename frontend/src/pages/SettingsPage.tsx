import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, Link } from 'react-router-dom'
import { logout, revokeAll } from '../features/auth/auth-api'
import { getMfaSettings } from '../features/auth/mfa-api'
import { useAuthStore } from '../features/auth/auth-store'
import {
  getNotificationPreferences,
  patchNotificationPreferences,
  type NotificationChannel,
  type NotificationPreferenceItem,
  type UserNotificationType,
} from '../features/notifications/notification-preferences-api'
import { Button } from '../shared/components/Button'
import { Card } from '../shared/components/Card'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { PageHeader } from '../shared/components/PageHeader'
import { canShowAdminNavigation } from '../features/admin/access/admin-access'
import { isMfaUiEnabled, isNotificationPreferencesEnabled } from '../shared/config/notifications-feature-flags'
import { isWebAuthnSupported } from '../shared/security/webauthn-support'

export function SettingsPage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const preferencesEnabled = isNotificationPreferencesEnabled()
  const mfaUiEnabled = isMfaUiEnabled()
  const webAuthnSupported = isWebAuthnSupported()
  const preferencesQuery = useQuery({
    queryKey: ['notification-preferences'],
    queryFn: getNotificationPreferences,
    enabled: preferencesEnabled,
  })
  const [draft, setDraft] = useState<Record<string, boolean>>({})
  const savePreferencesMutation = useMutation({
    mutationFn: (
      updates: Array<{ notificationType: UserNotificationType; channel: NotificationChannel; enabled: boolean }>,
    ) => patchNotificationPreferences(updates),
    onSuccess: (data) => {
      preferencesQuery.refetch()
      setDraft(extractDraft(data))
    },
  })
  const mfaSettingsQuery = useQuery({
    queryKey: ['mfa-settings'],
    queryFn: getMfaSettings,
    enabled: mfaUiEnabled,
  })
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

  const baseline = useMemo(() => extractDraft(preferencesQuery.data), [preferencesQuery.data])
  const effective = useMemo(() => ({ ...baseline, ...draft }), [baseline, draft])
  const dirty = Object.entries(effective).some(([key, value]) => baseline[key] !== value)

  const setToggle = (type: UserNotificationType, channel: NotificationChannel, enabled: boolean) => {
    setDraft((prev) => ({ ...prev, [`${type}::${channel}`]: enabled }))
  }

  const onSavePreferences = () => {
    if (!preferencesQuery.data) return
    const updates: Array<{ notificationType: UserNotificationType; channel: NotificationChannel; enabled: boolean }> =
      []
    for (const item of preferencesQuery.data) {
      for (const channel of ['IN_APP', 'EMAIL'] as const) {
        const key = `${item.notificationType}::${channel}`
        const nextEnabled = effective[key]
        if (nextEnabled !== undefined && nextEnabled !== item.channels[channel].enabled) {
          updates.push({ notificationType: item.notificationType, channel, enabled: nextEnabled })
        }
      }
    }
    if (updates.length > 0) {
      savePreferencesMutation.mutate(updates)
    }
  }

  return (
    <div className="space-y-3">
      <PageHeader title="Settings / Security" subtitle="Session and token controls" />
      <Card>
        <p className="mb-3 text-sm text-slate-600">
          Bearer mode may keep tokens in localStorage for local development; cookie mode uses HttpOnly
          cookies and CSRF headers (see docs/auth-cookie-csrf.md).
        </p>
        <div className="flex flex-wrap gap-2">
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
      {preferencesEnabled ? (
        <Card>
          <p className="text-sm font-medium text-slate-800">Notification preferences</p>
          <p className="mt-1 text-sm text-slate-600">Choose which events arrive in-app and by email.</p>
          <div className="mt-3 space-y-3">
            {preferencesQuery.data?.map((item) => (
              <div key={item.notificationType} className="rounded border border-slate-200 p-3">
                <p className="text-sm font-medium text-slate-800">{item.label}</p>
                <p className="text-xs text-slate-600">{item.description}</p>
                {(['IN_APP', 'EMAIL'] as const).map((channel) => {
                  const state = item.channels[channel]
                  const checked = effective[`${item.notificationType}::${channel}`] ?? state.enabled
                  return (
                    <label key={channel} className="mt-2 flex items-center justify-between gap-2 text-sm text-slate-700">
                      <span>{channel === 'IN_APP' ? 'In-app' : 'Email'}</span>
                      <input
                        aria-label={`${item.notificationType}-${channel}`}
                        type="checkbox"
                        checked={checked}
                        disabled={state.mandatory}
                        onChange={(e) => setToggle(item.notificationType, channel, e.target.checked)}
                      />
                    </label>
                  )
                })}
                {item.channels.IN_APP.mandatory || item.channels.EMAIL.mandatory ? (
                  <p className="mt-1 text-xs text-amber-700">Required for account security.</p>
                ) : null}
              </div>
            ))}
            {preferencesQuery.isError ? <ErrorAlert error={preferencesQuery.error} /> : null}
            {savePreferencesMutation.isError ? <ErrorAlert error={savePreferencesMutation.error} /> : null}
            {savePreferencesMutation.isSuccess ? (
              <p className="text-xs text-emerald-700">Preferences saved.</p>
            ) : null}
            <Button type="button" onClick={onSavePreferences} disabled={!dirty || savePreferencesMutation.isPending}>
              Save changes
            </Button>
          </div>
        </Card>
      ) : null}
      {mfaUiEnabled ? (
        <Card>
          <p className="text-sm font-medium text-slate-800">Multi-factor authentication</p>
          <p className="mt-1 text-sm text-slate-600">
            Security foundation for passkeys (WebAuthn) and recovery codes.
          </p>
          {mfaSettingsQuery.isError ? <ErrorAlert error={mfaSettingsQuery.error} /> : null}
          <div className="mt-3 space-y-2 text-sm text-slate-700">
            <p>Status: {mfaSettingsQuery.data?.webauthnEnabled ? 'WebAuthn/passkey enabled' : 'Not enabled'}</p>
            {!webAuthnSupported ? (
              <p className="text-amber-700">WebAuthn is not supported in this browser or secure context.</p>
            ) : null}
            {mfaSettingsQuery.data && !mfaSettingsQuery.data.mfaEnabled ? (
              <p className="text-slate-500">MFA setup is not enabled in this environment.</p>
            ) : null}
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button type="button" disabled>
              Set up passkey (coming soon)
            </Button>
            <Button type="button" disabled>
              Generate recovery codes (coming soon)
            </Button>
          </div>
        </Card>
      ) : null}
    </div>
  )
}

function extractDraft(items?: NotificationPreferenceItem[]) {
  const out: Record<string, boolean> = {}
  if (!items) return out
  for (const item of items) {
    out[`${item.notificationType}::IN_APP`] = item.channels.IN_APP.enabled
    out[`${item.notificationType}::EMAIL`] = item.channels.EMAIL.enabled
  }
  return out
}


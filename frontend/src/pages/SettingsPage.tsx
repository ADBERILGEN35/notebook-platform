import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, Link } from 'react-router-dom'
import { logout, revokeAll } from '../features/auth/auth-api'
import { getMfaSettings } from '../features/auth/mfa-api'
import {
  generateRecoveryCodes,
  registrationOptions,
  registrationVerify,
} from '../features/auth/mfa-api'
import { useAuthStore } from '../features/auth/auth-store'
import {
  getNotificationPreferences,
  getNotificationDeliveryPreferences,
  patchNotificationDeliveryPreferences,
  patchNotificationPreferences,
  type NotificationDeliveryPreference,
  type NotificationChannel,
  type NotificationPreferenceItem,
  type UserNotificationType,
} from '../features/notifications/notification-preferences-api'
import {
  getWorkspaceNotificationPreferences,
  patchWorkspaceNotificationPreferences,
  resetWorkspaceNotificationPreferences,
  type WorkspaceNotificationPreferencesResponse,
  type WorkspacePreferenceUpdate,
} from '../features/notifications/workspace-notification-preferences-api'
import { listWorkspaces } from '../features/workspaces/workspace-api'
import { useWorkspaceStore } from '../features/workspaces/workspace-store'
import { Button } from '../shared/components/Button'
import { Card } from '../shared/components/Card'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { Input } from '../shared/components/Input'
import { PageHeader } from '../shared/components/PageHeader'
import { canShowAdminNavigation } from '../features/admin/access/admin-access'
import {
  isMfaUiEnabled,
  isNotificationPreferencesEnabled,
  isWorkspaceNotificationPreferencesEnabled,
} from '../shared/config/notifications-feature-flags'
import { isWebAuthnSupported } from '../shared/security/webauthn-support'
import { clearOfflineNotes, listOfflineNotes } from '../features/offline/offline-note-cache'
import { listPendingDrafts } from '../features/offline/offline-note-drafts'
import {
  isOfflineEditEnabled,
  isOfflineNotesEnabled,
  isOfflineSyncEnabled,
} from '../shared/config/offline-feature-flags'

export function SettingsPage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const preferencesEnabled = isNotificationPreferencesEnabled()
  const workspacePrefsEnabled = isWorkspaceNotificationPreferencesEnabled()
  const mfaUiEnabled = isMfaUiEnabled()
  const webAuthnSupported = isWebAuthnSupported()
  const offlineNotesEnabled = isOfflineNotesEnabled()
  const offlineEditEnabled = isOfflineEditEnabled()
  const offlineSyncEnabled = isOfflineSyncEnabled()
  const preferencesQuery = useQuery({
    queryKey: ['notification-preferences'],
    queryFn: getNotificationPreferences,
    enabled: preferencesEnabled,
  })
  const [draft, setDraft] = useState<Record<string, boolean>>({})
  const [deliveryDraft, setDeliveryDraft] = useState<NotificationDeliveryPreference | null>(null)
  const savePreferencesMutation = useMutation({
    mutationFn: (
      updates: Array<{ notificationType: UserNotificationType; channel: NotificationChannel; enabled: boolean }>,
    ) => patchNotificationPreferences(updates),
    onSuccess: (data) => {
      preferencesQuery.refetch()
      setDraft(extractDraft(data))
    },
  })
  const deliveryQuery = useQuery({
    queryKey: ['notification-delivery-preferences'],
    queryFn: getNotificationDeliveryPreferences,
    enabled: preferencesEnabled,
  })
  const saveDeliveryMutation = useMutation({
    mutationFn: patchNotificationDeliveryPreferences,
    onSuccess: (data) => {
      deliveryQuery.refetch()
      setDeliveryDraft(data)
    },
  })
  const activeWorkspaceId = useWorkspaceStore((s) => s.activeWorkspaceId)
  const workspacesQuery = useQuery({
    queryKey: ['workspaces', 'settings-notification'],
    queryFn: () => listWorkspaces(0, 50),
    enabled: preferencesEnabled && workspacePrefsEnabled,
  })
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | null>(null)
  useEffect(() => {
    const items = workspacesQuery.data?.items ?? []
    if (!items.length) return
    const ids = new Set(items.map((w) => w.id))
    if (selectedWorkspaceId && ids.has(selectedWorkspaceId)) return
    if (activeWorkspaceId && ids.has(activeWorkspaceId)) {
      setSelectedWorkspaceId(activeWorkspaceId)
      return
    }
    setSelectedWorkspaceId(items[0].id)
  }, [workspacesQuery.data, activeWorkspaceId, selectedWorkspaceId])
  const workspacePrefsQuery = useQuery({
    queryKey: ['workspace-notification-preferences', selectedWorkspaceId],
    queryFn: () => getWorkspaceNotificationPreferences(selectedWorkspaceId!),
    enabled: Boolean(preferencesEnabled && workspacePrefsEnabled && selectedWorkspaceId),
  })
  const [workspaceDraft, setWorkspaceDraft] = useState<Record<string, WorkspaceChannelDraft>>({})
  const workspaceBaseline = useMemo(
    () => extractWorkspaceBaseline(workspacePrefsQuery.data),
    [workspacePrefsQuery.data],
  )
  const workspaceEffective = useMemo(
    () => ({ ...workspaceBaseline, ...workspaceDraft }),
    [workspaceBaseline, workspaceDraft],
  )
  const workspaceDirty = useMemo(() => {
    return Object.keys(workspaceEffective).some((key) => {
      const b = workspaceBaseline[key]
      const e = workspaceEffective[key]
      if (!b || !e) return false
      return b.inheritGlobal !== e.inheritGlobal || b.enabled !== e.enabled
    })
  }, [workspaceBaseline, workspaceEffective])
  const saveWorkspacePrefsMutation = useMutation({
    mutationFn: async () => {
      if (!selectedWorkspaceId) throw new Error('no workspace')
      const updates = buildWorkspacePreferenceUpdates(
        workspaceBaseline,
        workspaceEffective,
        workspacePrefsQuery.data,
      )
      return patchWorkspaceNotificationPreferences(selectedWorkspaceId, updates)
    },
    onSuccess: () => {
      void workspacePrefsQuery.refetch()
      setWorkspaceDraft({})
    },
  })
  const resetWorkspacePrefsMutation = useMutation({
    mutationFn: async () => {
      if (!selectedWorkspaceId) throw new Error('no workspace')
      return resetWorkspaceNotificationPreferences(selectedWorkspaceId)
    },
    onSuccess: () => {
      void workspacePrefsQuery.refetch()
      setWorkspaceDraft({})
    },
  })
  const mfaSettingsQuery = useQuery({
    queryKey: ['mfa-settings'],
    queryFn: getMfaSettings,
    enabled: mfaUiEnabled,
  })
  const [passkeyName, setPasskeyName] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null)
  const [ackSavedCodes, setAckSavedCodes] = useState(false)
  const offlineNotesQuery = useQuery({
    queryKey: ['offline-notes-count'],
    queryFn: async () => (await listOfflineNotes()).length,
    enabled: offlineNotesEnabled,
  })
  const offlineDraftsPendingQuery = useQuery({
    queryKey: ['offline-drafts-pending-count'],
    queryFn: async () => (await listPendingDrafts()).length,
    enabled: offlineNotesEnabled && offlineEditEnabled,
  })
  const clearOfflineMutation = useMutation({
    mutationFn: clearOfflineNotes,
    onSuccess: () => {
      void offlineNotesQuery.refetch()
      void offlineDraftsPendingQuery.refetch()
    },
  })
  const setupPasskeyMutation = useMutation({
    mutationFn: async () => {
      const options = await registrationOptions()
      const credential = (await navigator.credentials.create({
        publicKey: {
          challenge: Uint8Array.from(atob(options.challenge.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0)),
          rp: { id: options.rpId, name: options.rpName },
          user: {
            id: Uint8Array.from(atob(options.userId.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0)),
            name: options.userName,
            displayName: options.userDisplayName,
          },
          pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
          timeout: 60000,
          authenticatorSelection: {
            userVerification: options.userVerification as UserVerificationRequirement,
          },
          excludeCredentials: options.excludeCredentials.map((c) => ({
            type: 'public-key',
            id: Uint8Array.from(atob(c.id.replace(/-/g, '+').replace(/_/g, '/')), (ch) => ch.charCodeAt(0)),
          })),
        },
      })) as PublicKeyCredential | null
      if (!credential) throw new Error('Passkey cancelled')
      const attestation = credential.response as AuthenticatorAttestationResponse
      const publicKeyCose = btoa(String.fromCharCode(...new Uint8Array(attestation.attestationObject)))
      return registrationVerify({
        credentialId: credential.id,
        publicKeyCose,
        challenge: options.challenge,
        origin: window.location.origin,
        name: passkeyName || undefined,
      })
    },
    onSuccess: () => mfaSettingsQuery.refetch(),
  })
  const generateRecoveryCodesMutation = useMutation({
    mutationFn: (acknowledgeReplace: boolean) => generateRecoveryCodes(acknowledgeReplace),
    onSuccess: (data) => {
      setRecoveryCodes(data.codes)
      setAckSavedCodes(false)
    },
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

  const setWorkspaceChannel = (type: UserNotificationType, channel: NotificationChannel, enabled: boolean) => {
    const key = `${type}::${channel}`
    if (!workspaceBaseline[key]) return
    setWorkspaceDraft((prev) => ({
      ...prev,
      [key]: { inheritGlobal: false, enabled },
    }))
  }

  const setWorkspaceInherit = (type: UserNotificationType, channel: NotificationChannel, inheritGlobal: boolean) => {
    const key = `${type}::${channel}`
    const row = workspacePrefsQuery.data?.preferences.find((p) => p.notificationType === type)
    const st = row?.channels[channel]
    if (!st) return
    if (inheritGlobal) {
      setWorkspaceDraft((prev) => ({
        ...prev,
        [key]: { inheritGlobal: true, enabled: st.enabled ?? st.effectiveEnabled },
      }))
      return
    }
    setWorkspaceDraft((prev) => ({
      ...prev,
      [key]: { inheritGlobal: false, enabled: st.effectiveEnabled },
    }))
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
  const effectiveDelivery = deliveryDraft ?? deliveryQuery.data
  const deliveryInvalid =
    Boolean(effectiveDelivery?.quietHoursEnabled) &&
    (!effectiveDelivery?.quietHoursStart || !effectiveDelivery?.quietHoursEnd || !effectiveDelivery?.timezone)

  return (
    <div className="space-y-3">
      <PageHeader title="Settings / Security" subtitle="Session and token controls" />
      <Card>
        <p className="mb-3 text-sm text-slate-600">
          Bearer mode may keep tokens in localStorage for local development; cookie mode uses HttpOnly
          cookies and CSRF headers (see docs/auth-cookie-csrf.md).
        </p>
        {offlineNotesEnabled ? (
          <p className="mb-3 text-sm text-slate-600">
            Offline read mode caches opened notes in this browser. Offline editing is experimental and
            disabled unless explicitly enabled ({offlineEditEnabled ? 'on' : 'off'}). Automatic sync after
            reconnect remains off unless both offline edit and sync flags are enabled for evaluation (
            {offlineSyncEnabled ? 'sync flag on; no production worker in this release' : 'sync flag off'}).
            Logout clears this cache; use the button below for a manual wipe.
          </p>
        ) : null}
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => logoutMutation.mutate()}>
            Logout
          </Button>
          <Button className="bg-rose-600 text-white hover:bg-rose-700" onClick={() => revokeMutation.mutate()}>
            Revoke all sessions
          </Button>
          {offlineNotesEnabled ? (
            <Button
              type="button"
              onClick={() => {
                const confirmed = window.confirm(
                  'Remove cached offline notes and any local offline drafts from this browser?',
                )
                if (confirmed) {
                  clearOfflineMutation.mutate()
                }
              }}
            >
              Clear offline data — notes: {offlineNotesQuery.data ?? 0}
              {offlineEditEnabled ? `, pending drafts: ${offlineDraftsPendingQuery.data ?? 0}` : ''}
            </Button>
          ) : null}
        </div>
        {revokeMutation.isSuccess ? (
          <p className="mt-2 text-sm text-slate-600">Revoked: {revokeMutation.data.revokedCount}</p>
        ) : null}
        {revokeMutation.isError ? <ErrorAlert error={revokeMutation.error} /> : null}
        {clearOfflineMutation.isError ? <ErrorAlert error={clearOfflineMutation.error} /> : null}
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
        <>
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
          <div className="mt-4 border-t border-slate-200 pt-3">
            <p className="text-sm font-medium text-slate-800">Delivery schedule</p>
            <p className="mt-1 text-xs text-slate-600">Security notifications are always sent immediately.</p>
            <label className="mt-2 flex items-center justify-between gap-2 text-sm text-slate-700">
              <span>Email digest</span>
              <input
                aria-label="delivery-email-digest-enabled"
                type="checkbox"
                checked={effectiveDelivery?.emailDigestEnabled ?? false}
                onChange={(e) =>
                  setDeliveryDraft((prev) => ({
                    ...(prev ?? deliveryQuery.data ?? defaultDeliveryPreference()),
                    emailDigestEnabled: e.target.checked,
                  }))
                }
              />
            </label>
            <label className="mt-2 block text-sm text-slate-700">
              Digest frequency
              <select
                aria-label="delivery-email-digest-frequency"
                className="ml-2 rounded border border-slate-200 px-2 py-1 text-sm"
                value={effectiveDelivery?.emailDigestFrequency ?? 'DAILY'}
                onChange={(e) =>
                  setDeliveryDraft((prev) => ({
                    ...(prev ?? deliveryQuery.data ?? defaultDeliveryPreference()),
                    emailDigestFrequency: e.target.value as NotificationDeliveryPreference['emailDigestFrequency'],
                  }))
                }
              >
                <option value="NEVER">Never</option>
                <option value="DAILY">Daily</option>
                <option value="WEEKLY">Weekly</option>
              </select>
            </label>
            <label className="mt-2 flex items-center justify-between gap-2 text-sm text-slate-700">
              <span>Quiet hours</span>
              <input
                aria-label="delivery-quiet-hours-enabled"
                type="checkbox"
                checked={effectiveDelivery?.quietHoursEnabled ?? false}
                onChange={(e) =>
                  setDeliveryDraft((prev) => ({
                    ...(prev ?? deliveryQuery.data ?? defaultDeliveryPreference()),
                    quietHoursEnabled: e.target.checked,
                  }))
                }
              />
            </label>
            <div className="mt-2 flex flex-wrap gap-2">
              <Input
                placeholder="Start HH:mm"
                value={effectiveDelivery?.quietHoursStart ?? ''}
                onChange={(event) =>
                  setDeliveryDraft((prev) => ({
                    ...(prev ?? deliveryQuery.data ?? defaultDeliveryPreference()),
                    quietHoursStart: event.target.value,
                  }))
                }
              />
              <Input
                placeholder="End HH:mm"
                value={effectiveDelivery?.quietHoursEnd ?? ''}
                onChange={(event) =>
                  setDeliveryDraft((prev) => ({
                    ...(prev ?? deliveryQuery.data ?? defaultDeliveryPreference()),
                    quietHoursEnd: event.target.value,
                  }))
                }
              />
              <Input
                placeholder="Timezone, e.g. Europe/Istanbul"
                value={effectiveDelivery?.timezone ?? 'UTC'}
                onChange={(event) =>
                  setDeliveryDraft((prev) => ({
                    ...(prev ?? deliveryQuery.data ?? defaultDeliveryPreference()),
                    timezone: event.target.value,
                  }))
                }
              />
            </div>
            {deliveryInvalid ? <p className="mt-1 text-xs text-rose-700">Quiet hours requires start, end and timezone.</p> : null}
            {saveDeliveryMutation.isError ? <ErrorAlert error={saveDeliveryMutation.error} /> : null}
            {saveDeliveryMutation.isSuccess ? <p className="mt-1 text-xs text-emerald-700">Delivery preferences saved.</p> : null}
            <Button
              type="button"
              className="mt-2"
              onClick={() => effectiveDelivery && saveDeliveryMutation.mutate(effectiveDelivery)}
              disabled={!effectiveDelivery || deliveryInvalid || saveDeliveryMutation.isPending}
            >
              Save delivery schedule
            </Button>
          </div>
        </Card>
        {workspacePrefsEnabled ? (
          <Card>
            <p className="text-sm font-medium text-slate-800">Workspace notification preferences</p>
            <p className="mt-1 text-sm text-slate-600">
              Per-workspace overrides for notification channels. Email digest timing and quiet hours stay global (see
              delivery schedule above).
            </p>
            {workspacesQuery.isLoading ? <p className="mt-2 text-xs text-slate-500">Loading workspaces…</p> : null}
            {workspacesQuery.isError ? <ErrorAlert error={workspacesQuery.error} /> : null}
            {workspacesQuery.data?.items.length ? (
              <label className="mt-3 block text-sm text-slate-700">
                Workspace
                <select
                  aria-label="workspace-notification-workspace"
                  className="ml-2 rounded border border-slate-200 px-2 py-1 text-sm"
                  value={selectedWorkspaceId ?? ''}
                  onChange={(e) => {
                    setSelectedWorkspaceId(e.target.value || null)
                    setWorkspaceDraft({})
                  }}
                >
                  {workspacesQuery.data.items.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              !workspacesQuery.isLoading ? (
                <p className="mt-2 text-xs text-slate-600">No workspaces yet; create one to manage overrides.</p>
              ) : null
            )}
            {workspacePrefsQuery.isError ? <ErrorAlert error={workspacePrefsQuery.error} /> : null}
            <div className="mt-3 space-y-3">
              {workspacePrefsQuery.data?.preferences.map((item) => (
                <div key={item.notificationType} className="rounded border border-slate-200 p-3">
                  <p className="text-sm font-medium text-slate-800">{item.label}</p>
                  <p className="text-xs text-slate-600">{item.description}</p>
                  {(['IN_APP', 'EMAIL'] as const).map((channel) => {
                    const st = item.channels[channel]
                    const key = `${item.notificationType}::${channel}`
                    const eff = workspaceEffective[key]
                    if (!eff) return null
                    return (
                      <div key={channel} className="mt-2 space-y-1 rounded border border-slate-100 p-2">
                        <p className="text-xs font-medium text-slate-700">{channel === 'IN_APP' ? 'In-app' : 'Email'}</p>
                        <p className="text-[11px] text-slate-500">
                          {eff.inheritGlobal
                            ? `Using global: ${eff.enabled ? 'on' : 'off'} · effective ${st.effectiveEnabled ? 'on' : 'off'}`
                            : `Overridden: ${eff.enabled ? 'on' : 'off'} · effective ${st.effectiveEnabled ? 'on' : 'off'}`}
                        </p>
                        <label className="flex items-center justify-between gap-2 text-sm text-slate-700">
                          <span>Use global setting</span>
                          <input
                            aria-label={`workspace-${item.notificationType}-${channel}-inherit`}
                            type="checkbox"
                            checked={eff.inheritGlobal}
                            disabled={st.mandatory}
                            onChange={(e) => setWorkspaceInherit(item.notificationType, channel, e.target.checked)}
                          />
                        </label>
                        <label className="flex items-center justify-between gap-2 text-sm text-slate-700">
                          <span>{channel === 'IN_APP' ? 'In-app' : 'Email'} enabled</span>
                          <input
                            aria-label={`workspace-${item.notificationType}-${channel}`}
                            type="checkbox"
                            checked={eff.enabled}
                            disabled={st.mandatory || eff.inheritGlobal}
                            onChange={(e) => setWorkspaceChannel(item.notificationType, channel, e.target.checked)}
                          />
                        </label>
                        {st.mandatory ? (
                          <p className="text-[11px] text-amber-700">Required; cannot override per workspace.</p>
                        ) : null}
                      </div>
                    )
                  })}
                </div>
              ))}
            </div>
            {saveWorkspacePrefsMutation.isError ? <ErrorAlert error={saveWorkspacePrefsMutation.error} /> : null}
            {resetWorkspacePrefsMutation.isError ? <ErrorAlert error={resetWorkspacePrefsMutation.error} /> : null}
            {saveWorkspacePrefsMutation.isSuccess ? (
              <p className="mt-2 text-xs text-emerald-700">Workspace preferences saved.</p>
            ) : null}
            {resetWorkspacePrefsMutation.isSuccess ? (
              <p className="mt-2 text-xs text-emerald-700">Workspace preferences reset to global defaults.</p>
            ) : null}
            <div className="mt-3 flex flex-wrap gap-2">
              <Button
                type="button"
                onClick={() => saveWorkspacePrefsMutation.mutate()}
                disabled={
                  !selectedWorkspaceId ||
                  !workspaceDirty ||
                  saveWorkspacePrefsMutation.isPending ||
                  workspacePrefsQuery.isLoading
                }
              >
                Save workspace overrides
              </Button>
              <Button
                type="button"
                className="bg-slate-100 text-slate-800 hover:bg-slate-200"
                onClick={() => resetWorkspacePrefsMutation.mutate()}
                disabled={!selectedWorkspaceId || resetWorkspacePrefsMutation.isPending}
              >
                Reset workspace preferences
              </Button>
            </div>
          </Card>
        ) : null}
        </>
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
            <p>Active credentials: {mfaSettingsQuery.data?.activeCredentialCount ?? 0}</p>
            <p>Recovery codes remaining: {mfaSettingsQuery.data?.recoveryCodesRemaining ?? 0}</p>
            {!webAuthnSupported ? (
              <p className="text-amber-700">WebAuthn is not supported in this browser or secure context.</p>
            ) : null}
            {canShowAdminNavigation(user) && !(mfaSettingsQuery.data?.webauthnEnabled ?? false) ? (
              <p className="rounded border border-amber-300 bg-amber-50 px-2 py-1 text-amber-800">
                Required for admin access: set up a passkey and recovery codes.
              </p>
            ) : null}
            {mfaSettingsQuery.data && !mfaSettingsQuery.data.mfaEnabled ? (
              <p className="text-slate-500">MFA setup is not enabled in this environment.</p>
            ) : null}
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            <Input
              placeholder="Passkey name (optional)"
              value={passkeyName}
              onChange={(event: React.ChangeEvent<HTMLInputElement>) => setPasskeyName(event.target.value)}
            />
            <Button type="button" disabled={!webAuthnSupported || !mfaSettingsQuery.data?.mfaEnabled} onClick={() => setupPasskeyMutation.mutate()}>
              Set up passkey
            </Button>
            <Button
              type="button"
              disabled={!mfaSettingsQuery.data?.mfaEnabled}
              onClick={() => generateRecoveryCodesMutation.mutate((mfaSettingsQuery.data?.recoveryCodesRemaining ?? 0) > 0)}
            >
              {(mfaSettingsQuery.data?.recoveryCodesRemaining ?? 0) > 0 ? 'Regenerate recovery codes' : 'Generate recovery codes'}
            </Button>
          </div>
          {setupPasskeyMutation.isError ? <ErrorAlert error={setupPasskeyMutation.error} /> : null}
          {generateRecoveryCodesMutation.isError ? <ErrorAlert error={generateRecoveryCodesMutation.error} /> : null}
          {recoveryCodes ? (
            <div className="mt-3 rounded border border-amber-300 bg-amber-50 p-2 text-xs text-amber-800">
              <p className="font-medium">Recovery codes (shown once):</p>
              <pre className="mt-1 whitespace-pre-wrap">{recoveryCodes.join('\n')}</pre>
              <label className="mt-2 flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={ackSavedCodes}
                  onChange={(e) => setAckSavedCodes(e.target.checked)}
                />
                I have saved these recovery codes.
              </label>
              {!ackSavedCodes ? (
                <p className="mt-1 text-[11px]">Save these codes now. You will not be able to view them again.</p>
              ) : null}
            </div>
          ) : null}
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

function defaultDeliveryPreference(): NotificationDeliveryPreference {
  return {
    emailDigestEnabled: false,
    emailDigestFrequency: 'DAILY',
    quietHoursEnabled: false,
    quietHoursStart: '22:00',
    quietHoursEnd: '08:00',
    timezone: 'UTC',
  }
}

type WorkspaceChannelDraft = { inheritGlobal: boolean; enabled: boolean }

function extractWorkspaceBaseline(
  data?: WorkspaceNotificationPreferencesResponse,
): Record<string, WorkspaceChannelDraft> {
  const out: Record<string, WorkspaceChannelDraft> = {}
  if (!data) return out
  for (const item of data.preferences) {
    for (const channel of ['IN_APP', 'EMAIL'] as const) {
      const st = item.channels[channel]
      const key = `${item.notificationType}::${channel}`
      out[key] = {
        inheritGlobal: st.inherited,
        enabled: st.enabled ?? st.effectiveEnabled,
      }
    }
  }
  return out
}

function buildWorkspacePreferenceUpdates(
  baseline: Record<string, WorkspaceChannelDraft>,
  effective: Record<string, WorkspaceChannelDraft>,
  data?: WorkspaceNotificationPreferencesResponse,
): WorkspacePreferenceUpdate[] {
  if (!data) return []
  const updates: WorkspacePreferenceUpdate[] = []
  for (const item of data.preferences) {
    for (const channel of ['IN_APP', 'EMAIL'] as const) {
      const key = `${item.notificationType}::${channel}`
      const b = baseline[key]
      const e = effective[key]
      if (!b || !e) continue
      if (b.inheritGlobal === e.inheritGlobal && b.enabled === e.enabled) continue
      if (e.inheritGlobal) {
        updates.push({ notificationType: item.notificationType, channel, inheritGlobal: true })
      } else {
        updates.push({
          notificationType: item.notificationType,
          channel,
          inheritGlobal: false,
          enabled: e.enabled,
        })
      }
    }
  }
  return updates
}


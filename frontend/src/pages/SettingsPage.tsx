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
import {
  getWorkspaceNotificationPolicies,
  patchWorkspaceNotificationPolicies,
  resetWorkspaceNotificationPolicies,
  type WorkspaceNotificationPoliciesResponse,
  type WorkspaceNotificationPolicyMode,
  type WorkspaceNotificationPolicyUpdate,
} from '../features/notifications/workspace-notification-policies-api'
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
  isWorkspaceNotificationPoliciesEnabled,
  isWorkspaceNotificationPreferencesEnabled,
} from '../shared/config/notifications-feature-flags'
import { isWebAuthnSupported } from '../shared/security/webauthn-support'
import { clearOfflineNotes, listOfflineNotes } from '../features/offline/offline-note-cache'
import {
  deleteOfflineDraft,
  listOfflineDraftOverview,
  listOfflineDrafts,
  listPendingDrafts,
} from '../features/offline/offline-note-drafts'
import {
  isOfflineBackgroundSyncEnabled,
  isOfflineCacheEncryptionEnabled,
  offlineBackgroundSyncMode,
  isOfflineDraftEncryptionRequired,
  isOfflineEditEnabled,
  isOfflineEncryptionEnabled,
  isOfflineNotesEnabled,
  isOfflineSyncEnabled,
  offlineSyncRolloutMode,
} from '../shared/config/offline-feature-flags'
import { refreshOfflineDraftDiagnostics, syncOfflineDraft, syncPendingDrafts } from '../features/offline/offline-sync-service'
import { hasOfflineEncryptionKey, isOfflineCryptoSupported } from '../features/offline/offline-crypto'
import { getOfflineSyncDiagnostics } from '../features/offline/offline-sync-diagnostics'
import { runForegroundBackgroundSync } from '../features/offline/offline-background-sync-service'
import {
  getBackgroundSyncModePreference,
  setBackgroundSyncModePreference,
} from '../features/offline/offline-sync-preferences'

export function SettingsPage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const preferencesEnabled = isNotificationPreferencesEnabled()
  const workspacePrefsEnabled = isWorkspaceNotificationPreferencesEnabled()
  const workspacePoliciesEnabled = isWorkspaceNotificationPoliciesEnabled()
  const mfaUiEnabled = isMfaUiEnabled()
  const webAuthnSupported = isWebAuthnSupported()
  const offlineNotesEnabled = isOfflineNotesEnabled()
  const offlineEditEnabled = isOfflineEditEnabled()
  const offlineSyncEnabled = isOfflineSyncEnabled()
  const syncRolloutMode = offlineSyncRolloutMode()
  const backgroundSyncEnabled = isOfflineBackgroundSyncEnabled()
  const runtimeBackgroundMode = offlineBackgroundSyncMode()
  const offlineEncryptionEnabled = isOfflineEncryptionEnabled()
  const offlineDraftEncryptionRequired = isOfflineDraftEncryptionRequired()
  const offlineCacheEncryptionEnabled = isOfflineCacheEncryptionEnabled()
  const offlineCryptoSupported = isOfflineCryptoSupported()
  const offlineKeyActive = hasOfflineEncryptionKey()
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
    enabled: preferencesEnabled && (workspacePrefsEnabled || workspacePoliciesEnabled),
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
  const workspacePoliciesQuery = useQuery({
    queryKey: ['workspace-notification-policies', selectedWorkspaceId],
    queryFn: () => getWorkspaceNotificationPolicies(selectedWorkspaceId!),
    enabled: Boolean(preferencesEnabled && workspacePoliciesEnabled && selectedWorkspaceId),
  })
  const [policyDraft, setPolicyDraft] = useState<
    Record<string, { policyMode: WorkspaceNotificationPolicyMode; reason: string }>
  >({})
  const policyBaseline = useMemo(
    () => extractPolicyBaseline(workspacePoliciesQuery.data),
    [workspacePoliciesQuery.data],
  )
  const policyEffective = useMemo(
    () => ({ ...policyBaseline, ...policyDraft }),
    [policyBaseline, policyDraft],
  )
  const policyDirty = useMemo(() => {
    return Object.keys(policyEffective).some((key) => {
      const b = policyBaseline[key]
      const e = policyEffective[key]
      if (!b || !e) return false
      return b.policyMode !== e.policyMode || b.reason !== e.reason
    })
  }, [policyBaseline, policyEffective])
  useEffect(() => {
    setPolicyDraft({})
  }, [selectedWorkspaceId, workspacePoliciesQuery.data?.workspaceId])
  const saveWorkspacePoliciesMutation = useMutation({
    mutationFn: async () => {
      if (!selectedWorkspaceId) throw new Error('no workspace')
      const updates = buildPolicyUpdates(
        policyBaseline,
        policyEffective,
        workspacePoliciesQuery.data,
      )
      return patchWorkspaceNotificationPolicies(selectedWorkspaceId, updates)
    },
    onSuccess: () => {
      void workspacePoliciesQuery.refetch()
      setPolicyDraft({})
    },
  })
  const resetWorkspacePoliciesMutation = useMutation({
    mutationFn: async () => {
      if (!selectedWorkspaceId) throw new Error('no workspace')
      return resetWorkspaceNotificationPolicies(selectedWorkspaceId)
    },
    onSuccess: () => {
      void workspacePoliciesQuery.refetch()
      setPolicyDraft({})
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
  const offlineDraftsQuery = useQuery({
    queryKey: ['offline-drafts'],
    queryFn: listOfflineDrafts,
    enabled: offlineNotesEnabled && offlineEditEnabled,
  })
  const offlineDraftsOverviewQuery = useQuery({
    queryKey: ['offline-drafts-overview'],
    queryFn: listOfflineDraftOverview,
    enabled: offlineNotesEnabled && offlineEditEnabled,
  })
  const clearOfflineMutation = useMutation({
    mutationFn: clearOfflineNotes,
    onSuccess: () => {
      void offlineNotesQuery.refetch()
      void offlineDraftsPendingQuery.refetch()
      void offlineDraftsQuery.refetch()
      void offlineDraftsOverviewQuery.refetch()
    },
  })
  const syncDraftMutation = useMutation({
    mutationFn: syncOfflineDraft,
    onSuccess: () => {
      void offlineDraftsPendingQuery.refetch()
      void offlineDraftsQuery.refetch()
      void offlineNotesQuery.refetch()
      void offlineDraftsOverviewQuery.refetch()
    },
  })
  const discardDraftMutation = useMutation({
    mutationFn: deleteOfflineDraft,
    onSuccess: () => {
      void offlineDraftsPendingQuery.refetch()
      void offlineDraftsQuery.refetch()
      void offlineDraftsOverviewQuery.refetch()
    },
  })
  const syncAllMutation = useMutation({
    mutationFn: syncPendingDrafts,
    onSuccess: async () => {
      await refreshOfflineDraftDiagnostics()
      void offlineDraftsPendingQuery.refetch()
      void offlineDraftsQuery.refetch()
      void offlineDraftsOverviewQuery.refetch()
      void offlineNotesQuery.refetch()
    },
  })
  const discardFailedMutation = useMutation({
    mutationFn: async () => {
      const rows = await listOfflineDrafts()
      await Promise.all(
        rows.filter((r) => r.status === 'FAILED').map((r) => deleteOfflineDraft(r.noteId)),
      )
    },
    onSuccess: () => {
      void offlineDraftsPendingQuery.refetch()
      void offlineDraftsQuery.refetch()
      void offlineDraftsOverviewQuery.refetch()
    },
  })
  const diagnostics = getOfflineSyncDiagnostics()
  const [backgroundModePreference, setBackgroundModePreference] = useState(getBackgroundSyncModePreference())
  const effectiveBackgroundMode = backgroundModePreference ?? runtimeBackgroundMode
  const [lastBackgroundSummary, setLastBackgroundSummary] = useState<Awaited<
    ReturnType<typeof runForegroundBackgroundSync>
  > | null>(null)
  const runBackgroundSyncMutation = useMutation({
    mutationFn: () =>
      runForegroundBackgroundSync({
        authenticated: Boolean(user),
        encryptionReady: !offlineEncryptionEnabled || offlineKeyActive || !offlineDraftEncryptionRequired,
        modeOverride: effectiveBackgroundMode,
        allowPromptExecution: true,
      }),
    onSuccess: async (summary) => {
      setLastBackgroundSummary(summary)
      await refreshOfflineDraftDiagnostics()
      void offlineDraftsPendingQuery.refetch()
      void offlineDraftsQuery.refetch()
      void offlineDraftsOverviewQuery.refetch()
      void offlineNotesQuery.refetch()
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
    const st = workspacePrefsQuery.data?.preferences.find((p) => p.notificationType === type)?.channels[channel]
    if (st?.lockedByPolicy) return
    setWorkspaceDraft((prev) => ({
      ...prev,
      [key]: { inheritGlobal: false, enabled },
    }))
  }

  const setWorkspaceInherit = (type: UserNotificationType, channel: NotificationChannel, inheritGlobal: boolean) => {
    const key = `${type}::${channel}`
    const row = workspacePrefsQuery.data?.preferences.find((p) => p.notificationType === type)
    const st = row?.channels[channel]
    if (!st || st.lockedByPolicy) return
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
        {offlineNotesEnabled ? (
          <p className="mb-3 text-sm text-slate-600">
            Offline encryption: {offlineEncryptionEnabled ? 'enabled' : 'disabled'} | WebCrypto:{' '}
            {offlineCryptoSupported ? 'supported' : 'unsupported'} | Session key:{' '}
            {offlineKeyActive ? 'active' : 'not active'} | Draft encryption required:{' '}
            {offlineDraftEncryptionRequired ? 'yes' : 'no'} | Cache encryption:{' '}
            {offlineCacheEncryptionEnabled ? 'enabled' : 'disabled'}
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
          {offlineNotesEnabled && offlineEncryptionEnabled && !offlineKeyActive ? (
            <Button
              type="button"
              onClick={() => {
                const confirmed = window.confirm(
                  'Encrypted offline data is locked for this session. Clear stale offline data now?',
                )
                if (confirmed) clearOfflineMutation.mutate()
              }}
            >
              Clear stale encrypted offline data
            </Button>
          ) : null}
        </div>
        {revokeMutation.isSuccess ? (
          <p className="mt-2 text-sm text-slate-600">Revoked: {revokeMutation.data.revokedCount}</p>
        ) : null}
        {revokeMutation.isError ? <ErrorAlert error={revokeMutation.error} /> : null}
        {clearOfflineMutation.isError ? <ErrorAlert error={clearOfflineMutation.error} /> : null}
      </Card>
      {offlineNotesEnabled && offlineEditEnabled ? (
        <Card>
          <p className="text-sm font-medium text-slate-800">Offline drafts</p>
          <p className="mt-1 text-sm text-slate-600">
            Drafts are stored locally on this browser. Shared devices should clear offline data after use.
          </p>
          <p className="mt-1 text-xs text-slate-500">
            Rollout mode: {syncRolloutMode} · Pending: {diagnostics.draftsPending} · Conflict:{' '}
            {diagnostics.draftsConflict} · Failed: {diagnostics.draftsFailed}
          </p>
          <p className="mt-1 text-xs text-slate-500">
            Background sync: {backgroundSyncEnabled ? 'enabled' : 'disabled'} · Mode: {effectiveBackgroundMode}
          </p>
          <div className="mt-2 flex items-center gap-2 text-sm text-slate-700">
            <label htmlFor="background-sync-mode">Background mode</label>
            <select
              id="background-sync-mode"
              value={effectiveBackgroundMode}
              disabled={!backgroundSyncEnabled}
              onChange={(e) => {
                const value = e.target.value as 'disabled' | 'prompt' | 'auto_safe'
                setBackgroundModePreference(value)
                setBackgroundSyncModePreference(value)
              }}
              className="rounded border border-slate-200 px-2 py-1 text-sm"
            >
              <option value="disabled">Disabled</option>
              <option value="prompt">Prompt before syncing</option>
              <option value="auto_safe">Auto-sync safe drafts</option>
            </select>
          </div>
          {(backgroundSyncEnabled && effectiveBackgroundMode === 'prompt' && (offlineDraftsPendingQuery.data ?? 0) > 0) ? (
            <p className="mt-2 text-xs text-amber-700">You have drafts ready to sync. Review or run Sync all pending.</p>
          ) : null}
          <div className="mt-2 flex flex-wrap gap-2">
            <Button
              type="button"
              onClick={() => syncAllMutation.mutate()}
              disabled={!offlineSyncEnabled || syncRolloutMode === 'disabled' || syncAllMutation.isPending}
            >
              Sync all pending
            </Button>
            <Button
              type="button"
              onClick={() => runBackgroundSyncMutation.mutate()}
              disabled={!backgroundSyncEnabled || runBackgroundSyncMutation.isPending}
            >
              Run foreground background sync
            </Button>
            <Button
              type="button"
              onClick={() => {
                const confirmed = window.confirm('Discard all failed drafts?')
                if (confirmed) discardFailedMutation.mutate()
              }}
              disabled={discardFailedMutation.isPending}
            >
              Discard all failed drafts
            </Button>
          </div>
          <div data-testid="offline-drafts-list" className="mt-3 space-y-2">
            {(offlineDraftsOverviewQuery.data ?? []).length === 0 ? (
              <p className="text-xs text-slate-500">No offline drafts.</p>
            ) : (
              (offlineDraftsOverviewQuery.data ?? []).map((draftRow) => (
                <div key={draftRow.noteId} className="rounded border border-slate-200 p-2">
                  <p className="text-sm font-medium text-slate-800">{draftRow.title}</p>
                  <p className="text-xs text-slate-500">
                    Status: {draftRow.status}
                    {draftRow.locked ? ' (locked)' : ''} · Attempts: {draftRow.attemptCount} · Last edited:{' '}
                    {new Date(draftRow.lastEditedAt).toLocaleString()} · Note: {draftRow.noteId.slice(0, 8)} · WS:{' '}
                    {draftRow.workspaceId.slice(0, 8)}
                  </p>
                  {draftRow.lastError ? <p className="text-xs text-rose-700">Error: {draftRow.lastError}</p> : null}
                  <div className="mt-2 flex flex-wrap gap-2">
                    <Link className="text-sm text-primary-600 hover:underline" to={`/app/notes/${draftRow.noteId}`}>
                      Open
                    </Link>
                    <Button
                      type="button"
                      data-testid="offline-draft-sync-button"
                      onClick={() => syncDraftMutation.mutate(draftRow.noteId)}
                      disabled={
                        !navigator.onLine ||
                        !offlineSyncEnabled ||
                        syncRolloutMode === 'disabled' ||
                        syncDraftMutation.isPending ||
                        draftRow.locked ||
                        draftRow.status === 'CONFLICT'
                      }
                    >
                      Sync now
                    </Button>
                    <Button
                      type="button"
                      data-testid="offline-draft-discard-button"
                      onClick={() => discardDraftMutation.mutate(draftRow.noteId)}
                      disabled={discardDraftMutation.isPending}
                    >
                      Discard
                    </Button>
                    {(draftRow.status === 'FAILED' || draftRow.status === 'CONFLICT') && !draftRow.locked ? (
                      <Button
                        type="button"
                        onClick={() => syncDraftMutation.mutate(draftRow.noteId)}
                        disabled={syncDraftMutation.isPending || syncRolloutMode === 'disabled'}
                      >
                        Retry
                      </Button>
                    ) : null}
                  </div>
                </div>
              ))
            )}
          </div>
          {!offlineSyncEnabled || syncRolloutMode === 'disabled' ? (
            <p className="mt-2 text-xs text-amber-700">Sync is disabled in this environment.</p>
          ) : null}
          {syncAllMutation.isError ? <ErrorAlert error={syncAllMutation.error} /> : null}
          {syncDraftMutation.isError ? <ErrorAlert error={syncDraftMutation.error} /> : null}
          {discardDraftMutation.isError ? <ErrorAlert error={discardDraftMutation.error} /> : null}
          {discardFailedMutation.isError ? <ErrorAlert error={discardFailedMutation.error} /> : null}
          {runBackgroundSyncMutation.isError ? <ErrorAlert error={runBackgroundSyncMutation.error} /> : null}
          {lastBackgroundSummary ? (
            <p className="mt-2 text-xs text-slate-600">
              Last background sync: attempted {lastBackgroundSummary.attempted}, synced {lastBackgroundSummary.synced},
              conflicts {lastBackgroundSummary.conflicts}, failed {lastBackgroundSummary.failed}, skipped{' '}
              {lastBackgroundSummary.skipped}
              {lastBackgroundSummary.needsUserConsent ? ' (needs user consent)' : ''}
            </p>
          ) : null}
          {diagnostics.lastBackgroundSyncResult ? (
            <div className="mt-1 text-[11px] text-slate-500">
              <p>Diagnostics: {diagnostics.lastBackgroundSyncResult}</p>
              <p>
                Last run: {diagnostics.lastBackgroundSyncStartedAt ?? '-'} {'->'}{' '}
                {diagnostics.lastBackgroundSyncCompletedAt ?? '-'} | Mode:{' '}
                {diagnostics.lastBackgroundSyncMode ?? '-'} | Stopped:{' '}
                {diagnostics.backgroundStoppedReason ?? '-'}
              </p>
              {diagnostics.backgroundSkippedReasons ? (
                <p>Skipped reasons: {JSON.stringify(diagnostics.backgroundSkippedReasons)}</p>
              ) : null}
            </div>
          ) : null}
        </Card>
      ) : null}
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
        {workspacePrefsEnabled || workspacePoliciesEnabled ? (
          <Card>
            <p className="text-sm font-medium text-slate-800">
              {workspacePrefsEnabled ? 'Workspace notification preferences' : 'Workspace notifications'}
            </p>
            <p className="mt-1 text-sm text-slate-600">
              {workspacePrefsEnabled
                ? 'Per-workspace overrides for notification channels. Email digest timing and quiet hours stay global (see delivery schedule above).'
                : 'Workspace-level notification governance for all members.'}{' '}
              {workspacePoliciesEnabled
                ? 'Workspace owners and admins can set mandatory channel policies; they apply to every member.'
                : null}
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
                    setPolicyDraft({})
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
                <p className="mt-2 text-xs text-slate-600">No workspaces yet; create one to manage settings.</p>
              ) : null
            )}
            {workspacePoliciesEnabled ? (
              <>
                <p className="mt-4 text-sm font-medium text-slate-800">Admin notification policies</p>
                <p className="mt-1 rounded border border-amber-200 bg-amber-50 p-2 text-[11px] text-amber-900">
                  Policies affect all members of this workspace. Force-enabled channels stay on regardless of member
                  preference; force-disabled channels stay off. Digest and quiet hours scheduling remain global per user.
                </p>
                {workspacePoliciesQuery.isError ? <ErrorAlert error={workspacePoliciesQuery.error} /> : null}
                <div className="mt-3 space-y-3">
                  {workspacePoliciesQuery.data?.policies.map((row) => (
                    <div key={row.notificationType} className="rounded border border-slate-200 p-3">
                      <p className="text-sm font-medium text-slate-800">{row.label}</p>
                      {(['IN_APP', 'EMAIL'] as const).map((channel) => {
                        const key = `${row.notificationType}::${channel}`
                        const eff = policyEffective[key]
                        if (!eff) return null
                        const canEdit =
                          Boolean(workspacePoliciesQuery.data?.canManagePolicies) &&
                          row.channels[channel].manageable
                        return (
                          <div key={channel} className="mt-2 space-y-1 rounded border border-slate-100 p-2">
                            <p className="text-xs font-medium text-slate-700">
                              {channel === 'IN_APP' ? 'In-app' : 'Email'}
                            </p>
                            <label className="block text-xs text-slate-700">
                              Policy mode
                              <select
                                aria-label={`policy-${row.notificationType}-${channel}-mode`}
                                className="ml-2 mt-1 block rounded border border-slate-200 px-2 py-1 text-sm"
                                disabled={!canEdit}
                                value={eff.policyMode}
                                onChange={(e) => {
                                  const v = e.target.value as WorkspaceNotificationPolicyMode
                                  setPolicyDraft((prev) => ({
                                    ...prev,
                                    [key]: { policyMode: v, reason: eff.reason },
                                  }))
                                }}
                              >
                                <option value="USER_CONTROLLED">User controlled</option>
                                <option value="FORCE_ENABLED">Force enabled</option>
                                <option value="FORCE_DISABLED">Force disabled</option>
                              </select>
                            </label>
                            <label className="mt-1 block text-xs text-slate-700">
                              Reason (required for force modes when server requires it)
                              <input
                                aria-label={`policy-${row.notificationType}-${channel}-reason`}
                                className="mt-1 w-full rounded border border-slate-200 px-2 py-1 text-sm"
                                disabled={!canEdit}
                                value={eff.reason}
                                onChange={(e) =>
                                  setPolicyDraft((prev) => ({
                                    ...prev,
                                    [key]: { policyMode: eff.policyMode, reason: e.target.value },
                                  }))
                                }
                              />
                            </label>
                            {!canEdit ? (
                              <p className="text-[11px] text-slate-500">
                                Only workspace owners and admins can edit policies.
                              </p>
                            ) : null}
                          </div>
                        )
                      })}
                    </div>
                  ))}
                </div>
                {saveWorkspacePoliciesMutation.isError ? (
                  <ErrorAlert error={saveWorkspacePoliciesMutation.error} />
                ) : null}
                {resetWorkspacePoliciesMutation.isError ? (
                  <ErrorAlert error={resetWorkspacePoliciesMutation.error} />
                ) : null}
                {saveWorkspacePoliciesMutation.isSuccess ? (
                  <p className="mt-2 text-xs text-emerald-700">Workspace policies saved.</p>
                ) : null}
                {resetWorkspacePoliciesMutation.isSuccess ? (
                  <p className="mt-2 text-xs text-emerald-700">Workspace policies reset to user-controlled defaults.</p>
                ) : null}
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button
                    type="button"
                    onClick={() => saveWorkspacePoliciesMutation.mutate()}
                    disabled={
                      !selectedWorkspaceId ||
                      !policyDirty ||
                      !workspacePoliciesQuery.data?.canManagePolicies ||
                      saveWorkspacePoliciesMutation.isPending ||
                      workspacePoliciesQuery.isLoading
                    }
                  >
                    Save workspace policies
                  </Button>
                  <Button
                    type="button"
                    className="bg-slate-100 text-slate-800 hover:bg-slate-200"
                    onClick={() => {
                      if (window.confirm('Reset all workspace notification policies to user-controlled defaults?')) {
                        resetWorkspacePoliciesMutation.mutate()
                      }
                    }}
                    disabled={
                      !selectedWorkspaceId ||
                      !workspacePoliciesQuery.data?.canManagePolicies ||
                      resetWorkspacePoliciesMutation.isPending
                    }
                  >
                    Reset workspace policies
                  </Button>
                </div>
              </>
            ) : null}
            {workspacePrefsEnabled ? (
              <>
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
                            <p className="text-xs font-medium text-slate-700">
                              {channel === 'IN_APP' ? 'In-app' : 'Email'}
                            </p>
                            <p className="text-[11px] text-slate-500">
                              {eff.inheritGlobal
                                ? `Using global: ${eff.enabled ? 'on' : 'off'} · effective ${st.effectiveEnabled ? 'on' : 'off'}`
                                : `Overridden: ${eff.enabled ? 'on' : 'off'} · effective ${st.effectiveEnabled ? 'on' : 'off'}`}
                            </p>
                            {st.lockedByPolicy && st.workspacePolicy ? (
                              <p className="text-[11px] text-amber-800">
                                Required by workspace policy ({st.workspacePolicy.policyMode}
                                {st.workspacePolicy.reason ? `: ${st.workspacePolicy.reason}` : ''}).
                              </p>
                            ) : null}
                            <label className="flex items-center justify-between gap-2 text-sm text-slate-700">
                              <span>Use global setting</span>
                              <input
                                aria-label={`workspace-${item.notificationType}-${channel}-inherit`}
                                type="checkbox"
                                checked={eff.inheritGlobal}
                                disabled={st.mandatory || st.lockedByPolicy}
                                onChange={(e) => setWorkspaceInherit(item.notificationType, channel, e.target.checked)}
                              />
                            </label>
                            <label className="flex items-center justify-between gap-2 text-sm text-slate-700">
                              <span>{channel === 'IN_APP' ? 'In-app' : 'Email'} enabled</span>
                              <input
                                aria-label={`workspace-${item.notificationType}-${channel}`}
                                type="checkbox"
                                checked={eff.enabled}
                                disabled={st.mandatory || eff.inheritGlobal || st.lockedByPolicy}
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
              </>
            ) : null}
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

type PolicyChannelDraft = { policyMode: WorkspaceNotificationPolicyMode; reason: string }

function extractPolicyBaseline(
  data?: WorkspaceNotificationPoliciesResponse,
): Record<string, PolicyChannelDraft> {
  const out: Record<string, PolicyChannelDraft> = {}
  if (!data) return out
  for (const row of data.policies) {
    for (const channel of ['IN_APP', 'EMAIL'] as const) {
      const st = row.channels[channel]
      const key = `${row.notificationType}::${channel}`
      out[key] = { policyMode: st.policyMode, reason: st.reason ?? '' }
    }
  }
  return out
}

function buildPolicyUpdates(
  baseline: Record<string, PolicyChannelDraft>,
  effective: Record<string, PolicyChannelDraft>,
  data?: WorkspaceNotificationPoliciesResponse,
): WorkspaceNotificationPolicyUpdate[] {
  if (!data) return []
  const updates: WorkspaceNotificationPolicyUpdate[] = []
  for (const row of data.policies) {
    for (const channel of ['IN_APP', 'EMAIL'] as const) {
      const key = `${row.notificationType}::${channel}`
      const b = baseline[key]
      const e = effective[key]
      if (!b || !e) continue
      if (b.policyMode === e.policyMode && b.reason === e.reason) continue
      updates.push({
        notificationType: row.notificationType,
        channel,
        policyMode: e.policyMode,
        reason: e.reason.trim() === '' ? null : e.reason,
      })
    }
  }
  return updates
}

import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  getNotificationPreferences,
  patchNotificationPreferences,
  type NotificationChannel,
  type UserNotificationType,
} from '../features/notifications/notification-preferences-api'
import { isNotificationPreferencesEnabled } from '../shared/config/notifications-feature-flags'
import { PageHeader } from '../shared/components/PageHeader'
import { SectionCard } from '../shared/components/SectionCard'
import { PermissionDenied } from '../shared/components/PermissionDenied'
import { LoadingState } from '../shared/components/LoadingState'
import { ErrorState } from '../shared/components/ErrorState'
import { Button } from '../shared/components/Button'
import { PreferenceToggle } from '../features/settings/PreferenceToggle'

export function NotificationPreferencesPage() {
  const enabled = isNotificationPreferencesEnabled()
  const preferencesQuery = useQuery({
    queryKey: ['notification-preferences'],
    queryFn: getNotificationPreferences,
    enabled,
  })
  const [draft, setDraft] = useState<Record<string, boolean>>({})

  useEffect(() => {
    if (!preferencesQuery.data) return
    const next: Record<string, boolean> = {}
    for (const item of preferencesQuery.data) {
      next[`${item.notificationType}::IN_APP`] = item.channels.IN_APP.enabled
      next[`${item.notificationType}::EMAIL`] = item.channels.EMAIL.enabled
    }
    setDraft(next)
  }, [preferencesQuery.data])

  const saveMutation = useMutation({
    mutationFn: (updates: Array<{ notificationType: UserNotificationType; channel: NotificationChannel; enabled: boolean }>) =>
      patchNotificationPreferences(updates),
    onSuccess: () => void preferencesQuery.refetch(),
  })

  const dirty = useMemo(() => {
    if (!preferencesQuery.data) return false
    for (const item of preferencesQuery.data) {
      for (const channel of ['IN_APP', 'EMAIL'] as const) {
        const key = `${item.notificationType}::${channel}`
        if (draft[key] !== undefined && draft[key] !== item.channels[channel].enabled) return true
      }
    }
    return false
  }, [draft, preferencesQuery.data])

  if (!enabled) {
    return (
      <PermissionDenied title="Preferences disabled" message="Notification preferences are not enabled in this environment." />
    )
  }

  const onSave = () => {
    if (!preferencesQuery.data) return
    const updates: Array<{ notificationType: UserNotificationType; channel: NotificationChannel; enabled: boolean }> = []
    for (const item of preferencesQuery.data) {
      for (const channel of ['IN_APP', 'EMAIL'] as const) {
        const key = `${item.notificationType}::${channel}`
        const next = draft[key]
        if (next !== undefined && next !== item.channels[channel].enabled) {
          updates.push({ notificationType: item.notificationType, channel, enabled: next })
        }
      }
    }
    if (updates.length) saveMutation.mutate(updates)
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Notification preferences" subtitle="Global delivery channels per event type." />
      {preferencesQuery.isLoading ? <LoadingState label="Loading preferences…" /> : null}
      {preferencesQuery.isError ? <ErrorState error={preferencesQuery.error} /> : null}
      {preferencesQuery.data ? (
        <SectionCard title="Event channels" description="Mandatory security alerts cannot be disabled.">
          <ul className="space-y-3">
            {preferencesQuery.data.map((item) => (
              <li key={item.notificationType} className="rounded-lg border border-outline-variant p-3">
                <p className="font-medium text-on-surface">{item.label}</p>
                <p className="text-label-md text-on-surface-variant">{item.description}</p>
                <div className="mt-2 space-y-2">
                  {(['IN_APP', 'EMAIL'] as const).map((channel) => {
                    const state = item.channels[channel]
                    const key = `${item.notificationType}::${channel}`
                    return (
                      <PreferenceToggle
                        key={channel}
                        label={channel === 'IN_APP' ? 'In-app' : 'Email'}
                        checked={draft[key] ?? state.enabled}
                        disabled={state.mandatory}
                        locked={state.mandatory}
                        onChange={(checked) => setDraft((prev) => ({ ...prev, [key]: checked }))}
                      />
                    )
                  })}
                </div>
              </li>
            ))}
          </ul>
          <Button type="button" className="mt-4 bg-primary text-white hover:bg-primary-container" disabled={!dirty || saveMutation.isPending} onClick={onSave}>
            Save preferences
          </Button>
          {saveMutation.isError ? <ErrorState error={saveMutation.error} className="mt-3" /> : null}
        </SectionCard>
      ) : null}
      <p className="text-label-md text-on-surface-variant">
        Workspace-level overrides and admin policies are available when workspace notification flags are enabled (see legacy settings or admin console).
      </p>
    </div>
  )
}


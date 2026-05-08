const parseBool = (raw: string | boolean | undefined, defaultValue = false): boolean => {
  if (raw === undefined || raw === null || raw === '') return defaultValue
  if (typeof raw === 'boolean') return raw
  return String(raw).toLowerCase() === 'true' || raw === '1'
}

export const isNotificationsEnabled = (): boolean =>
  parseBool(window.__NOTEBOOK_CONFIG__?.NOTIFICATIONS_ENABLED ?? import.meta.env.VITE_NOTIFICATIONS_ENABLED, false)

export const isNotificationPreferencesEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.NOTIFICATION_PREFERENCES_ENABLED ??
      import.meta.env.VITE_NOTIFICATION_PREFERENCES_ENABLED,
    false,
  )

export const isWorkspaceNotificationPreferencesEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED ??
      import.meta.env.VITE_WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED,
    false,
  )

export const isNotificationsSseEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.NOTIFICATIONS_SSE_ENABLED ?? import.meta.env.VITE_NOTIFICATIONS_SSE_ENABLED,
    true,
  )

export const isMfaUiEnabled = (): boolean =>
  parseBool(window.__NOTEBOOK_CONFIG__?.MFA_UI_ENABLED ?? import.meta.env.VITE_MFA_UI_ENABLED, false)

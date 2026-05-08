const parseBool = (raw: string | boolean | undefined, defaultValue = false): boolean => {
  if (raw === undefined || raw === null || raw === '') return defaultValue
  if (typeof raw === 'boolean') return raw
  return String(raw).toLowerCase() === 'true' || raw === '1'
}

export const isNotificationsEnabled = (): boolean =>
  parseBool(window.__NOTEBOOK_CONFIG__?.NOTIFICATIONS_ENABLED ?? import.meta.env.VITE_NOTIFICATIONS_ENABLED, false)

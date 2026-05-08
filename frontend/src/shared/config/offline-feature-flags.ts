const parseBool = (raw: string | boolean | undefined, defaultValue = false): boolean => {
  if (raw === undefined || raw === null || raw === '') return defaultValue
  if (typeof raw === 'boolean') return raw
  return String(raw).toLowerCase() === 'true' || raw === '1'
}

const parseNumber = (raw: string | number | undefined, defaultValue: number): number => {
  if (raw === undefined || raw === null || raw === '') return defaultValue
  const value = Number(raw)
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : defaultValue
}

export const isPwaEnabled = (): boolean =>
  parseBool(window.__NOTEBOOK_CONFIG__?.PWA_ENABLED ?? import.meta.env.VITE_PWA_ENABLED, false)

export const isOfflineNotesEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_NOTES_ENABLED ?? import.meta.env.VITE_OFFLINE_NOTES_ENABLED,
    false,
  )

export const offlineNotesMaxItems = (): number =>
  parseNumber(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_NOTES_MAX_ITEMS ?? import.meta.env.VITE_OFFLINE_NOTES_MAX_ITEMS,
    50,
  )

/** Experimental: local IndexedDB drafts for offline edits (default off). */
export const isOfflineEditEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_EDIT_ENABLED ?? import.meta.env.VITE_OFFLINE_EDIT_ENABLED,
    false,
  )

/** Future: automatic background sync after reconnect (default off; not implemented in Faz 67). */
export const isOfflineSyncEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_SYNC_ENABLED ?? import.meta.env.VITE_OFFLINE_SYNC_ENABLED,
    false,
  )

export const offlineEditMaxDrafts = (): number =>
  parseNumber(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_EDIT_MAX_DRAFTS ?? import.meta.env.VITE_OFFLINE_EDIT_MAX_DRAFTS,
    50,
  )

export const offlineEditMaxDraftAgeDays = (): number =>
  parseNumber(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_EDIT_MAX_DRAFT_AGE_DAYS ??
      import.meta.env.VITE_OFFLINE_EDIT_MAX_DRAFT_AGE_DAYS,
    7,
  )

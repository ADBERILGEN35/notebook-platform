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

export const isOfflineEncryptionEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_ENCRYPTION_ENABLED ?? import.meta.env.VITE_OFFLINE_ENCRYPTION_ENABLED,
    false,
  )

export const isOfflineDraftEncryptionRequired = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_DRAFT_ENCRYPTION_REQUIRED ??
      import.meta.env.VITE_OFFLINE_DRAFT_ENCRYPTION_REQUIRED,
    false,
  )

export const isOfflineCacheEncryptionEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_CACHE_ENCRYPTION_ENABLED ??
      import.meta.env.VITE_OFFLINE_CACHE_ENCRYPTION_ENABLED,
    false,
  )

export const offlineSyncRolloutMode = (): 'disabled' | 'manual' | 'guarded' => {
  const raw =
    window.__NOTEBOOK_CONFIG__?.OFFLINE_SYNC_ROLLOUT_MODE ??
    import.meta.env.VITE_OFFLINE_SYNC_ROLLOUT_MODE ??
    'disabled'
  const value = String(raw).toLowerCase()
  if (value === 'manual' || value === 'guarded') return value
  return 'disabled'
}

export const offlineSyncMaxAttempts = (): number =>
  parseNumber(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_SYNC_MAX_ATTEMPTS ?? import.meta.env.VITE_OFFLINE_SYNC_MAX_ATTEMPTS,
    5,
  )

export const offlineSyncStaleMinutes = (): number =>
  parseNumber(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_SYNC_STALE_MINUTES ?? import.meta.env.VITE_OFFLINE_SYNC_STALE_MINUTES,
    15,
  )

export const isBackendMergeAnalysisEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.BACKEND_MERGE_ANALYSIS_ENABLED ??
      import.meta.env.VITE_BACKEND_MERGE_ANALYSIS_ENABLED,
    false,
  )

export const isBackendMergeApplyEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.BACKEND_MERGE_APPLY_ENABLED ??
      import.meta.env.VITE_BACKEND_MERGE_APPLY_ENABLED,
    false,
  )

export const isOfflineBackgroundSyncEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_BACKGROUND_SYNC_ENABLED ??
      import.meta.env.VITE_OFFLINE_BACKGROUND_SYNC_ENABLED,
    false,
  )

export const offlineBackgroundSyncMode = (): 'disabled' | 'prompt' | 'auto_safe' => {
  const raw =
    window.__NOTEBOOK_CONFIG__?.OFFLINE_BACKGROUND_SYNC_MODE ??
    import.meta.env.VITE_OFFLINE_BACKGROUND_SYNC_MODE ??
    'disabled'
  const value = String(raw).toLowerCase()
  if (value === 'prompt' || value === 'auto_safe') return value
  return 'disabled'
}

export const offlineBackgroundSyncMaxBatch = (): number =>
  parseNumber(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_BACKGROUND_SYNC_MAX_BATCH ??
      import.meta.env.VITE_OFFLINE_BACKGROUND_SYNC_MAX_BATCH,
    5,
  )

export const offlineBackgroundSyncMinIntervalSeconds = (): number =>
  parseNumber(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_BACKGROUND_SYNC_MIN_INTERVAL_SECONDS ??
      import.meta.env.VITE_OFFLINE_BACKGROUND_SYNC_MIN_INTERVAL_SECONDS,
    60,
  )

export const offlineBackgroundSyncRequireUnmetered = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_BACKGROUND_SYNC_REQUIRE_UNMETERED ??
      import.meta.env.VITE_OFFLINE_BACKGROUND_SYNC_REQUIRE_UNMETERED,
    false,
  )

export const offlineBackgroundSyncRequireCharging = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.OFFLINE_BACKGROUND_SYNC_REQUIRE_CHARGING ??
      import.meta.env.VITE_OFFLINE_BACKGROUND_SYNC_REQUIRE_CHARGING,
    false,
  )

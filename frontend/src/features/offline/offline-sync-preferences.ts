import type { OfflineBackgroundSyncMode } from './offline-background-sync-types'

const KEY = 'offline_sync_preferences_v1'

type StoredPreferences = {
  backgroundSyncMode?: OfflineBackgroundSyncMode
  updatedAt?: string
}

export function getBackgroundSyncModePreference(): OfflineBackgroundSyncMode | null {
  try {
    const raw = window.localStorage.getItem(KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as StoredPreferences
    const mode = parsed.backgroundSyncMode
    if (mode === 'disabled' || mode === 'prompt' || mode === 'auto_safe') return mode
    return null
  } catch {
    return null
  }
}

export function setBackgroundSyncModePreference(mode: OfflineBackgroundSyncMode): void {
  const payload: StoredPreferences = { backgroundSyncMode: mode, updatedAt: new Date().toISOString() }
  try {
    window.localStorage.setItem(KEY, JSON.stringify(payload))
  } catch {
    // best-effort local preference persistence
  }
}

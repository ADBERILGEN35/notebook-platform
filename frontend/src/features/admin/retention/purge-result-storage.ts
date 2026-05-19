import type { RetentionRunResponse } from '../notification-retention-api'

const KEY = 'admin.retention.purgeResult.v1'

export type StoredPurgeResult = {
  requestId: string
  recordedAt: string
  actorLabel: string
  result: RetentionRunResponse
}

export function savePurgeResult(payload: StoredPurgeResult): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(payload))
  } catch {
    /* ignore quota */
  }
}

export function loadPurgeResult(): StoredPurgeResult | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    return JSON.parse(raw) as StoredPurgeResult
  } catch {
    return null
  }
}

export function clearPurgeResult(): void {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    /* ignore */
  }
}

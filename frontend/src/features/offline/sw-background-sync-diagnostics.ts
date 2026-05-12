import { OFFLINE_SW_SYNC_DIAGNOSTICS_STORE, openOfflineDb } from './offline-db'
import {
  SW_BACKGROUND_SYNC_DIAGNOSTICS_ID,
  type SwBackgroundSyncSummary,
} from './sw-background-sync-types'

let latestSummary: SwBackgroundSyncSummary | null = null

export async function setSwBackgroundSyncSummary(summary: SwBackgroundSyncSummary): Promise<void> {
  latestSummary = summary
  try {
    const db = await openOfflineDb()
    await db.put(OFFLINE_SW_SYNC_DIAGNOSTICS_STORE, summary)
  } catch {
    // Diagnostics must never block foreground use or service worker event completion.
  }
}

export async function getSwBackgroundSyncSummary(): Promise<SwBackgroundSyncSummary | null> {
  try {
    const db = await openOfflineDb()
    const row = (await db.get(
      OFFLINE_SW_SYNC_DIAGNOSTICS_STORE,
      SW_BACKGROUND_SYNC_DIAGNOSTICS_ID,
    )) as SwBackgroundSyncSummary | undefined
    latestSummary = row ?? latestSummary
    return row ?? latestSummary
  } catch {
    return latestSummary
  }
}

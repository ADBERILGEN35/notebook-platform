import type { OfflineNoteDraftRecord } from './offline-sync-types'
import type { OfflineSyncResult } from './offline-sync-service'

export type OfflineBackgroundSyncMode = 'disabled' | 'prompt' | 'auto_safe'

export type BackgroundSyncSkipReason =
  | 'conflict'
  | 'failed'
  | 'locked'
  | 'missing_base_etag'
  | 'max_attempts'
  | 'stale_or_too_old'
  | 'session_unavailable'
  | 'network_guardrail'
  | 'encryption_key_unavailable'
  | 'requires_user_review'
  | 'currently_editing'
  | 'syncing'
  | 'synced'
  | 'status_not_eligible'
  | 'batch_limit'

export type BackgroundSyncEligibleDraft = {
  noteId: string
  draftId: string
}

export type BackgroundSyncSkippedDraft = {
  noteId: string
  draftId: string
  reason: BackgroundSyncSkipReason
}

export type BackgroundSyncSelection = {
  eligible: BackgroundSyncEligibleDraft[]
  skipped: BackgroundSyncSkippedDraft[]
}

export type BackgroundSyncSummary = {
  startedAt: string
  completedAt: string
  attempted: number
  synced: number
  conflicts: number
  failed: number
  queued: number
  skipped: number
  needsUserConsent: boolean
  mode: OfflineBackgroundSyncMode
  stopReason: string | null
  eligibleCount: number
  skippedReasons: Record<BackgroundSyncSkipReason, number>
}

export type BackgroundSyncExecutionDeps = {
  listDrafts: () => Promise<OfflineNoteDraftRecord[]>
  syncDraft: (noteId: string) => Promise<OfflineSyncResult>
}

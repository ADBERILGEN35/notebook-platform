import type { OfflineDraftOverview } from './offline-note-drafts'

export const SW_BACKGROUND_SYNC_TAG = 'offline-draft-sync'
export const SW_BACKGROUND_SYNC_DIAGNOSTICS_ID = 'latest'

export type SwBackgroundSyncSupportStatus = {
  serviceWorkerSupported: boolean
  syncManagerSupported: boolean
  supported: boolean
  reason: 'supported' | 'service_worker_unsupported' | 'sync_manager_unsupported'
}

export type SwBackgroundSyncSkipReason =
  | 'conflict'
  | 'failed'
  | 'locked'
  | 'encrypted_key_unavailable'
  | 'missing_base_etag'
  | 'max_attempts'
  | 'session_unavailable'
  | 'csrf_unavailable'
  | 'currently_editing_unknown'
  | 'browser_unsupported'
  | 'status_not_eligible'
  | 'batch_limit'
  | 'network_unavailable'

export type SwBackgroundSyncCandidate = Pick<
  OfflineDraftOverview,
  'noteId' | 'draftId' | 'status' | 'attemptCount' | 'locked'
> & {
  baseEtag?: string | null
}

export type SwBackgroundSyncSelection = {
  eligible: Array<{ noteId: string; draftId: string }>
  skipped: Array<{ noteId: string; draftId: string; reason: SwBackgroundSyncSkipReason }>
}

export type SwBackgroundSyncSummary = {
  id: typeof SW_BACKGROUND_SYNC_DIAGNOSTICS_ID
  startedAt: string
  completedAt: string
  mode: 'dry-run'
  eligible: number
  skipped: number
  skipReasons: Record<SwBackgroundSyncSkipReason, number>
  supported: boolean
  registered: boolean
  dryRunOnly: boolean
  stopReason: string | null
}

export type SwBackgroundSyncRegistrationStatus = {
  supported: boolean
  registered: boolean
  reason:
    | 'registered'
    | 'disabled'
    | 'register_disabled'
    | 'browser_unsupported'
    | 'service_worker_unavailable'
    | 'registration_failed'
  tags: string[]
}

import { offlineEditMaxDraftAgeDays, offlineSyncMaxAttempts } from '../../shared/config/offline-feature-flags'
import type { OfflineNoteDraftRecord } from './offline-sync-types'
import type {
  BackgroundSyncSelection,
  BackgroundSyncSkipReason,
  OfflineBackgroundSyncMode,
} from './offline-background-sync-types'

export function selectEligibleDraftsForBackgroundSync(
  drafts: OfflineNoteDraftRecord[],
  options?: { maxBatch?: number; maxAgeDays?: number; maxAttempts?: number; activeNoteId?: string | null },
): BackgroundSyncSelection {
  const maxBatch = Math.max(1, options?.maxBatch ?? 5)
  const maxAgeDays = Math.max(1, options?.maxAgeDays ?? offlineEditMaxDraftAgeDays())
  const maxAttempts = Math.max(1, options?.maxAttempts ?? offlineSyncMaxAttempts())
  const now = Date.now()
  const maxAgeMs = maxAgeDays * 24 * 60 * 60 * 1000

  const eligible: BackgroundSyncSelection['eligible'] = []
  const skipped: BackgroundSyncSelection['skipped'] = []

  const pushSkip = (draft: OfflineNoteDraftRecord, reason: BackgroundSyncSkipReason) =>
    skipped.push({ noteId: draft.noteId, draftId: draft.draftId, reason })

  for (const draft of drafts) {
    if (options?.activeNoteId && draft.noteId === options.activeNoteId) {
      pushSkip(draft, 'currently_editing')
      continue
    }
    if (draft.status === 'CONFLICT') {
      pushSkip(draft, 'conflict')
      continue
    }
    if (draft.status === 'FAILED') {
      pushSkip(draft, 'failed')
      continue
    }
    if (draft.status === 'SYNCING') {
      pushSkip(draft, 'syncing')
      continue
    }
    if (draft.status === 'SYNCED') {
      pushSkip(draft, 'synced')
      continue
    }
    if (!['DRAFT', 'QUEUED'].includes(draft.status)) {
      pushSkip(draft, 'status_not_eligible')
      continue
    }
    if (!draft.baseEtag) {
      pushSkip(draft, 'missing_base_etag')
      continue
    }
    if (draft.attemptCount >= maxAttempts) {
      pushSkip(draft, 'max_attempts')
      continue
    }
    const age = now - Date.parse(draft.lastEditedAt)
    if (Number.isFinite(age) && age > maxAgeMs) {
      pushSkip(draft, 'stale_or_too_old')
      continue
    }
    if (
      draft.lastError &&
      (draft.lastError.includes('ENCRYPTED_KEY_UNAVAILABLE') ||
        draft.lastError.includes('PERMISSION') ||
        draft.lastError.includes('NOTE_NOT_FOUND'))
    ) {
      pushSkip(draft, 'requires_user_review')
      continue
    }
    if (eligible.length >= maxBatch) {
      pushSkip(draft, 'batch_limit')
      continue
    }
    eligible.push({ noteId: draft.noteId, draftId: draft.draftId })
  }

  return { eligible, skipped }
}

export function evaluateBackgroundSyncTrigger(input: {
  mode: OfflineBackgroundSyncMode
  enabled: boolean
  isOnline: boolean
  authenticated: boolean
  encryptionReady: boolean
  requireUnmetered: boolean
  nowMs: number
  lastAttemptAtMs: number | null
  minIntervalSeconds: number
  saveData?: boolean
}): { canRun: boolean; needsUserConsent: boolean; reason: string | null } {
  if (!input.enabled || input.mode === 'disabled') {
    return { canRun: false, needsUserConsent: false, reason: 'DISABLED' }
  }
  if (!input.isOnline) return { canRun: false, needsUserConsent: false, reason: 'OFFLINE' }
  if (!input.authenticated) return { canRun: false, needsUserConsent: false, reason: 'UNAUTHENTICATED' }
  if (!input.encryptionReady) return { canRun: false, needsUserConsent: false, reason: 'ENCRYPTION_KEY_UNAVAILABLE' }
  if (input.requireUnmetered && input.saveData) {
    return { canRun: false, needsUserConsent: false, reason: 'SAVE_DATA_ENABLED' }
  }
  if (
    input.lastAttemptAtMs != null &&
    input.nowMs - input.lastAttemptAtMs < Math.max(1, input.minIntervalSeconds) * 1000
  ) {
    return { canRun: false, needsUserConsent: false, reason: 'MIN_INTERVAL_NOT_REACHED' }
  }
  if (input.mode === 'prompt') {
    return { canRun: false, needsUserConsent: true, reason: 'USER_CONSENT_REQUIRED' }
  }
  return { canRun: true, needsUserConsent: false, reason: null }
}

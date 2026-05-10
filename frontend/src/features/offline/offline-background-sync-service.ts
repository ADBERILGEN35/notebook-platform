import {
  isOfflineBackgroundSyncEnabled,
  offlineBackgroundSyncMaxBatch,
  offlineBackgroundSyncMinIntervalSeconds,
  offlineBackgroundSyncMode,
  offlineBackgroundSyncRequireUnmetered,
} from '../../shared/config/offline-feature-flags'
import { getOfflineSyncDiagnostics, setBackgroundSyncSummary } from './offline-sync-diagnostics'
import { listOfflineDrafts } from './offline-note-drafts'
import { syncOfflineDraft } from './offline-sync-service'
import {
  evaluateBackgroundSyncTrigger,
  selectEligibleDraftsForBackgroundSync,
} from './offline-background-sync-policy'
import type {
  BackgroundSyncExecutionDeps,
  BackgroundSyncSummary,
  BackgroundSyncSkipReason,
  OfflineBackgroundSyncMode,
} from './offline-background-sync-types'

function emptySkippedReasons(): Record<BackgroundSyncSkipReason, number> {
  return {
    conflict: 0,
    failed: 0,
    locked: 0,
    missing_base_etag: 0,
    max_attempts: 0,
    stale_or_too_old: 0,
    session_unavailable: 0,
    network_guardrail: 0,
    encryption_key_unavailable: 0,
    requires_user_review: 0,
    currently_editing: 0,
    syncing: 0,
    synced: 0,
    status_not_eligible: 0,
    batch_limit: 0,
  }
}

function createSummary(mode: OfflineBackgroundSyncMode): BackgroundSyncSummary {
  const now = new Date().toISOString()
  return {
    startedAt: now,
    completedAt: now,
    attempted: 0,
    synced: 0,
    conflicts: 0,
    failed: 0,
    queued: 0,
    skipped: 0,
    needsUserConsent: false,
    mode,
    stopReason: null,
    eligibleCount: 0,
    skippedReasons: emptySkippedReasons(),
  }
}

export async function runForegroundBackgroundSync(input: {
  authenticated: boolean
  encryptionReady: boolean
  modeOverride?: OfflineBackgroundSyncMode
  allowPromptExecution?: boolean
  activeNoteId?: string | null
  deps?: Partial<BackgroundSyncExecutionDeps>
}): Promise<BackgroundSyncSummary> {
  const mode = input.modeOverride ?? offlineBackgroundSyncMode()
  const summary = createSummary(mode)
  const diagnostics = getOfflineSyncDiagnostics()
  const trigger = evaluateBackgroundSyncTrigger({
    mode,
    enabled: isOfflineBackgroundSyncEnabled(),
    isOnline: navigator.onLine,
    authenticated: input.authenticated,
    encryptionReady: input.encryptionReady,
    requireUnmetered: offlineBackgroundSyncRequireUnmetered(),
    saveData: (navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData,
    nowMs: Date.now(),
    lastAttemptAtMs: diagnostics.lastBackgroundSyncCompletedAt
      ? Date.parse(diagnostics.lastBackgroundSyncCompletedAt)
      : null,
    minIntervalSeconds: offlineBackgroundSyncMinIntervalSeconds(),
  })

  const deps: BackgroundSyncExecutionDeps = {
    listDrafts: input.deps?.listDrafts ?? listOfflineDrafts,
    syncDraft: input.deps?.syncDraft ?? syncOfflineDraft,
  }

  const drafts = await deps.listDrafts()
  const selection = selectEligibleDraftsForBackgroundSync(drafts, {
    maxBatch: offlineBackgroundSyncMaxBatch(),
    activeNoteId: input.activeNoteId,
  })
  summary.skipped = selection.skipped.length
  summary.eligibleCount = selection.eligible.length
  for (const item of selection.skipped) {
    summary.skippedReasons[item.reason] = (summary.skippedReasons[item.reason] ?? 0) + 1
  }

  if (!trigger.canRun && !(trigger.needsUserConsent && input.allowPromptExecution)) {
    summary.needsUserConsent = trigger.needsUserConsent
    if (trigger.reason === 'UNAUTHENTICATED') {
      summary.skippedReasons.session_unavailable += summary.eligibleCount
    } else if (trigger.reason === 'ENCRYPTION_KEY_UNAVAILABLE') {
      summary.skippedReasons.encryption_key_unavailable += summary.eligibleCount
    } else if (trigger.reason === 'SAVE_DATA_ENABLED') {
      summary.skippedReasons.network_guardrail += summary.eligibleCount
    }
    summary.stopReason = trigger.reason
    summary.completedAt = new Date().toISOString()
    setBackgroundSyncSummary(summary)
    return summary
  }

  if (trigger.needsUserConsent && !input.allowPromptExecution) {
    summary.needsUserConsent = true
    summary.stopReason = 'USER_CONSENT_REQUIRED'
    summary.completedAt = new Date().toISOString()
    setBackgroundSyncSummary(summary)
    return summary
  }

  for (const candidate of selection.eligible) {
    summary.attempted += 1
    const result = await deps.syncDraft(candidate.noteId)
    if (result.status === 'synced') summary.synced += 1
    else if (result.status === 'conflict') summary.conflicts += 1
    else if (result.status === 'queued') summary.queued += 1
    else if (result.status === 'failed' || result.status === 'not_found' || result.status === 'locked') {
      summary.failed += 1
      if (result.status === 'locked') summary.skippedReasons.locked += 1
      if (result.reason?.includes('AUTH') || result.reason?.includes('SESSION')) {
        summary.stopReason = 'session_expired'
        break
      }
    }
  }
  summary.completedAt = new Date().toISOString()
  setBackgroundSyncSummary(summary)
  return summary
}

type OfflineSyncDiagnostics = {
  draftsPending: number
  draftsConflict: number
  draftsFailed: number
  lastSyncAttemptAt: string | null
  syncAttempts: number
  syncSuccess: number
  lastBackgroundSyncStartedAt: string | null
  lastBackgroundSyncCompletedAt: string | null
  lastBackgroundSyncMode: string | null
  backgroundAttempted: number
  backgroundSynced: number
  backgroundConflicts: number
  backgroundFailed: number
  backgroundQueued: number
  backgroundSkipped: number
  backgroundSkippedReasons: Record<string, number> | null
  backgroundStoppedReason: string | null
  lastBackgroundSyncResult: string | null
}

const diagnostics: OfflineSyncDiagnostics = {
  draftsPending: 0,
  draftsConflict: 0,
  draftsFailed: 0,
  lastSyncAttemptAt: null,
  syncAttempts: 0,
  syncSuccess: 0,
  lastBackgroundSyncStartedAt: null,
  lastBackgroundSyncCompletedAt: null,
  lastBackgroundSyncMode: null,
  backgroundAttempted: 0,
  backgroundSynced: 0,
  backgroundConflicts: 0,
  backgroundFailed: 0,
  backgroundQueued: 0,
  backgroundSkipped: 0,
  backgroundSkippedReasons: null,
  backgroundStoppedReason: null,
  lastBackgroundSyncResult: null,
}

export function markSyncAttempt() {
  diagnostics.syncAttempts += 1
  diagnostics.lastSyncAttemptAt = new Date().toISOString()
}

export function markSyncSuccess() {
  diagnostics.syncSuccess += 1
}

export function updateDraftCounters(input: { pending: number; conflict: number; failed: number }) {
  diagnostics.draftsPending = input.pending
  diagnostics.draftsConflict = input.conflict
  diagnostics.draftsFailed = input.failed
}

export function getOfflineSyncDiagnostics(): OfflineSyncDiagnostics {
  return { ...diagnostics }
}

export function setBackgroundSyncSummary(summary: {
  startedAt: string
  completedAt: string
  mode: string
  attempted: number
  synced: number
  conflicts: number
  failed: number
  queued: number
  skipped: number
  skippedReasons: Record<string, number>
  needsUserConsent: boolean
  stopReason: string | null
}) {
  diagnostics.lastBackgroundSyncStartedAt = summary.startedAt
  diagnostics.lastBackgroundSyncCompletedAt = summary.completedAt
  diagnostics.lastBackgroundSyncMode = summary.mode
  diagnostics.backgroundAttempted = summary.attempted
  diagnostics.backgroundSynced = summary.synced
  diagnostics.backgroundConflicts = summary.conflicts
  diagnostics.backgroundFailed = summary.failed
  diagnostics.backgroundQueued = summary.queued
  diagnostics.backgroundSkipped = summary.skipped
  diagnostics.backgroundSkippedReasons = { ...summary.skippedReasons }
  diagnostics.backgroundStoppedReason = summary.stopReason
  diagnostics.lastBackgroundSyncResult = JSON.stringify({
    mode: summary.mode,
    startedAt: summary.startedAt,
    attempted: summary.attempted,
    synced: summary.synced,
    conflicts: summary.conflicts,
    failed: summary.failed,
    queued: summary.queued,
    skipped: summary.skipped,
    skippedReasons: summary.skippedReasons,
    needsUserConsent: summary.needsUserConsent,
    stopReason: summary.stopReason,
  })
}

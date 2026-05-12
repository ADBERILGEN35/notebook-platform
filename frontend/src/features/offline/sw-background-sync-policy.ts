import { swBackgroundSyncMaxBatch } from '../../shared/config/offline-feature-flags'
import { offlineSyncMaxAttempts } from '../../shared/config/offline-feature-flags'
import type {
  SwBackgroundSyncCandidate,
  SwBackgroundSyncSelection,
  SwBackgroundSyncSkipReason,
  SwBackgroundSyncSupportStatus,
} from './sw-background-sync-types'

export function getSwBackgroundSyncSupport(win: Pick<Window, 'navigator'> = window): SwBackgroundSyncSupportStatus {
  const serviceWorkerSupported = 'serviceWorker' in win.navigator
  const syncManagerSupported =
    serviceWorkerSupported &&
    'SyncManager' in globalThis &&
    'ServiceWorkerRegistration' in globalThis &&
    'sync' in ServiceWorkerRegistration.prototype
  if (!serviceWorkerSupported) {
    return { serviceWorkerSupported, syncManagerSupported: false, supported: false, reason: 'service_worker_unsupported' }
  }
  if (!syncManagerSupported) {
    return { serviceWorkerSupported, syncManagerSupported: false, supported: false, reason: 'sync_manager_unsupported' }
  }
  return { serviceWorkerSupported, syncManagerSupported, supported: true, reason: 'supported' }
}

function emptySkipReasons(): Record<SwBackgroundSyncSkipReason, number> {
  return {
    conflict: 0,
    failed: 0,
    locked: 0,
    encrypted_key_unavailable: 0,
    missing_base_etag: 0,
    max_attempts: 0,
    session_unavailable: 0,
    csrf_unavailable: 0,
    currently_editing_unknown: 0,
    browser_unsupported: 0,
    status_not_eligible: 0,
    batch_limit: 0,
    network_unavailable: 0,
  }
}

export function createSwBackgroundSyncSkipReasonCounters() {
  return emptySkipReasons()
}

export function selectEligibleDraftsForSwBackgroundSync(
  drafts: SwBackgroundSyncCandidate[],
  options?: {
    maxAttempts?: number
    maxBatch?: number
    online?: boolean
    browserSupported?: boolean
    sessionAvailable?: boolean
    csrfAvailable?: boolean
  },
): SwBackgroundSyncSelection {
  const maxAttempts = Math.max(1, options?.maxAttempts ?? offlineSyncMaxAttempts())
  const maxBatch = Math.max(1, options?.maxBatch ?? swBackgroundSyncMaxBatch())
  const eligible: SwBackgroundSyncSelection['eligible'] = []
  const skipped: SwBackgroundSyncSelection['skipped'] = []
  const pushSkip = (draft: SwBackgroundSyncCandidate, reason: SwBackgroundSyncSkipReason) => {
    skipped.push({ noteId: draft.noteId, draftId: draft.draftId, reason })
  }

  for (const draft of drafts) {
    if (options?.browserSupported === false) {
      pushSkip(draft, 'browser_unsupported')
      continue
    }
    if (options?.online === false) {
      pushSkip(draft, 'network_unavailable')
      continue
    }
    if (options?.sessionAvailable === false) {
      pushSkip(draft, 'session_unavailable')
      continue
    }
    if (options?.csrfAvailable === false) {
      pushSkip(draft, 'csrf_unavailable')
      continue
    }
    if (draft.locked) {
      pushSkip(draft, 'encrypted_key_unavailable')
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
    if (eligible.length >= maxBatch) {
      pushSkip(draft, 'batch_limit')
      continue
    }
    eligible.push({ noteId: draft.noteId, draftId: draft.draftId })
  }

  return { eligible, skipped }
}

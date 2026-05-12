import {
  isSwBackgroundSyncDryRunOnly,
  isSwBackgroundSyncEnabled,
  isSwBackgroundSyncRegisterEnabled,
  swBackgroundSyncMaxBatch,
} from '../../shared/config/offline-feature-flags'
import { listOfflineDraftOverview } from './offline-note-drafts'
import { setSwBackgroundSyncSummary } from './sw-background-sync-diagnostics'
import { createSwBackgroundSyncSkipReasonCounters, getSwBackgroundSyncSupport, selectEligibleDraftsForSwBackgroundSync } from './sw-background-sync-policy'
import {
  SW_BACKGROUND_SYNC_DIAGNOSTICS_ID,
  SW_BACKGROUND_SYNC_TAG,
  type SwBackgroundSyncRegistrationStatus,
  type SwBackgroundSyncSummary,
} from './sw-background-sync-types'

type SyncCapableRegistration = ServiceWorkerRegistration & {
  sync?: {
    register: (tag: string) => Promise<void>
    getTags?: () => Promise<string[]>
  }
}

export async function registerSwBackgroundSync(): Promise<SwBackgroundSyncRegistrationStatus> {
  if (!isSwBackgroundSyncEnabled()) {
    return { supported: false, registered: false, reason: 'disabled', tags: [] }
  }
  if (!isSwBackgroundSyncRegisterEnabled()) {
    return { supported: false, registered: false, reason: 'register_disabled', tags: [] }
  }
  const support = getSwBackgroundSyncSupport()
  if (!support.supported) {
    return { supported: false, registered: false, reason: 'browser_unsupported', tags: [] }
  }
  try {
    const registration = (await navigator.serviceWorker.ready) as SyncCapableRegistration
    if (!registration.sync) {
      return { supported: false, registered: false, reason: 'service_worker_unavailable', tags: [] }
    }
    await registration.sync.register(SW_BACKGROUND_SYNC_TAG)
    const tags = registration.sync.getTags ? await registration.sync.getTags() : [SW_BACKGROUND_SYNC_TAG]
    return { supported: true, registered: tags.includes(SW_BACKGROUND_SYNC_TAG), reason: 'registered', tags }
  } catch {
    return { supported: support.supported, registered: false, reason: 'registration_failed', tags: [] }
  }
}

export async function runSwBackgroundSyncDryRun(input?: {
  browserSupported?: boolean
  registered?: boolean
  online?: boolean
  sessionAvailable?: boolean
  csrfAvailable?: boolean
}): Promise<SwBackgroundSyncSummary> {
  const startedAt = new Date().toISOString()
  const support = getSwBackgroundSyncSupport()
  const drafts = await listOfflineDraftOverview()
  const selection = selectEligibleDraftsForSwBackgroundSync(
    drafts.map((draft) => ({
      noteId: draft.noteId,
      draftId: draft.draftId,
      status: draft.status,
      attemptCount: draft.attemptCount,
      locked: draft.locked,
      baseEtag: draft.baseEtag,
    })),
    {
      browserSupported: input?.browserSupported ?? support.supported,
      online: input?.online ?? navigator.onLine,
      sessionAvailable: input?.sessionAvailable,
      csrfAvailable: input?.csrfAvailable,
      maxBatch: swBackgroundSyncMaxBatch(),
    },
  )
  const skipReasons = createSwBackgroundSyncSkipReasonCounters()
  for (const skipped of selection.skipped) {
    skipReasons[skipped.reason] = (skipReasons[skipped.reason] ?? 0) + 1
  }
  const dryRunOnly = isSwBackgroundSyncDryRunOnly()
  const summary: SwBackgroundSyncSummary = {
    id: SW_BACKGROUND_SYNC_DIAGNOSTICS_ID,
    startedAt,
    completedAt: new Date().toISOString(),
    mode: 'dry-run',
    eligible: selection.eligible.length,
    skipped: selection.skipped.length,
    skipReasons,
    supported: input?.browserSupported ?? support.supported,
    registered: input?.registered ?? false,
    dryRunOnly,
    stopReason: dryRunOnly ? null : 'REMOTE_WRITE_DISABLED_IN_PHASE_96',
  }
  await setSwBackgroundSyncSummary(summary)
  return summary
}

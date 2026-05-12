import { SW_BACKGROUND_SYNC_TAG, type SwBackgroundSyncSummary } from '../features/offline/sw-background-sync-types'

type SyncEventLike = Event & { tag?: string; waitUntil: (promise: Promise<unknown>) => void }
type ServiceWorkerScopeLike = {
  addEventListener: (type: 'sync', listener: (event: Event) => void) => void
}

export type OfflineSyncWorkerDeps = {
  dryRun: () => Promise<SwBackgroundSyncSummary>
}

export function installOfflineSyncWorker(scope: ServiceWorkerScopeLike, deps: OfflineSyncWorkerDeps): void {
  scope.addEventListener('sync', (event: Event) => {
    const syncEvent = event as SyncEventLike
    if (syncEvent.tag !== SW_BACKGROUND_SYNC_TAG) return
    syncEvent.waitUntil(deps.dryRun())
  })
}

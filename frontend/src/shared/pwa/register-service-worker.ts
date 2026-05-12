import { registerSW } from 'virtual:pwa-register'
import { isPwaEnabled } from '../config/offline-feature-flags'
import { registerSwBackgroundSync } from '../../features/offline/sw-background-sync-registration'

export function registerServiceWorker() {
  if (!isPwaEnabled() || import.meta.env.DEV) return
  registerSW({ immediate: true })
  void registerSwBackgroundSync()
}

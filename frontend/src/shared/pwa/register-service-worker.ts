import { registerSW } from 'virtual:pwa-register'
import { isPwaEnabled } from '../config/offline-feature-flags'

export function registerServiceWorker() {
  if (!isPwaEnabled() || import.meta.env.DEV) return
  registerSW({ immediate: true })
}

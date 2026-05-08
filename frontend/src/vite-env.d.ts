/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_AUTH_TRANSPORT?: string
  readonly VITE_NOTIFICATIONS_ENABLED?: string
  readonly VITE_NOTIFICATION_PREFERENCES_ENABLED?: string
  readonly VITE_MFA_UI_ENABLED?: string
  readonly VITE_ADMIN_UI_ENABLED?: string
  readonly VITE_ADMIN_UI_DEV_OPEN?: string
  readonly VITE_AUDIT_API_MODE?: string
  readonly VITE_SOURCEMAP?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

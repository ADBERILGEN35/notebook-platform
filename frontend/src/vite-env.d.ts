/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_AUTH_TRANSPORT?: string
  readonly VITE_SSO_ENABLED?: string
  readonly VITE_NOTIFICATIONS_ENABLED?: string
  readonly VITE_PWA_ENABLED?: string
  readonly VITE_OFFLINE_NOTES_ENABLED?: string
  readonly VITE_OFFLINE_NOTES_MAX_ITEMS?: string
  readonly VITE_NOTIFICATION_PREFERENCES_ENABLED?: string
  readonly VITE_MFA_UI_ENABLED?: string
  readonly VITE_ADMIN_UI_ENABLED?: string
  readonly VITE_ADMIN_UI_DEV_OPEN?: string
  readonly VITE_AUDIT_API_MODE?: string
  readonly VITE_ENTERPRISE_ADMIN_WRITE_ENABLED?: string
  readonly VITE_ENTERPRISE_ADMIN_APPROVALS_ENABLED?: string
  readonly VITE_ENTERPRISE_GITOPS_PR_ENABLED?: string
  readonly VITE_GITOPS_RBAC_ROLE_REQUESTS_ENABLED?: string
  readonly VITE_ADMIN_RBAC_UI_ENABLED?: string
  readonly VITE_ADMIN_RBAC_ROLE_REQUESTS_ENABLED?: string
  readonly VITE_NOTIFICATION_ANALYTICS_UI_ENABLED?: string
  readonly VITE_NOTIFICATION_DEAD_LETTER_UI_ENABLED?: string
  readonly VITE_NOTIFICATION_RETENTION_UI_ENABLED?: string
  readonly VITE_NOTIFICATION_RETENTION_PURGE_UI_ENABLED?: string
  readonly VITE_NOTIFICATION_LEGAL_HOLD_UI_ENABLED?: string
  readonly VITE_SOURCEMAP?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

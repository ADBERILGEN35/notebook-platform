export {}

declare global {
  interface Window {
    __NOTEBOOK_CONFIG__?: {
      API_BASE_URL?: string
      AUTH_TRANSPORT?: 'bearer' | 'cookie' | 'dual'
      SSO_ENABLED?: boolean | string
      PWA_ENABLED?: boolean | string
      OFFLINE_NOTES_ENABLED?: boolean | string
      OFFLINE_NOTES_MAX_ITEMS?: number | string
      OFFLINE_EDIT_ENABLED?: boolean | string
      OFFLINE_SYNC_ENABLED?: boolean | string
      OFFLINE_EDIT_MAX_DRAFTS?: number | string
      OFFLINE_EDIT_MAX_DRAFT_AGE_DAYS?: number | string
      NOTIFICATIONS_ENABLED?: boolean | string
      NOTIFICATIONS_SSE_ENABLED?: boolean | string
      NOTIFICATION_PREFERENCES_ENABLED?: boolean | string
      WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED?: boolean | string
      MFA_UI_ENABLED?: boolean | string
      ADMIN_UI_ENABLED?: boolean | string
      ADMIN_UI_DEV_OPEN?: boolean | string
      AUDIT_API_MODE?: 'mock' | 'real' | string
    }
  }
}

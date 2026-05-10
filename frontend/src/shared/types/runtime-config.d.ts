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
      OFFLINE_ENCRYPTION_ENABLED?: boolean | string
      OFFLINE_DRAFT_ENCRYPTION_REQUIRED?: boolean | string
      OFFLINE_CACHE_ENCRYPTION_ENABLED?: boolean | string
      OFFLINE_SYNC_ROLLOUT_MODE?: 'disabled' | 'manual' | 'guarded' | string
      OFFLINE_SYNC_MAX_ATTEMPTS?: number | string
      OFFLINE_SYNC_STALE_MINUTES?: number | string
      OFFLINE_BACKGROUND_SYNC_ENABLED?: boolean | string
      OFFLINE_BACKGROUND_SYNC_MODE?: 'disabled' | 'prompt' | 'auto_safe' | string
      OFFLINE_BACKGROUND_SYNC_MAX_BATCH?: number | string
      OFFLINE_BACKGROUND_SYNC_MIN_INTERVAL_SECONDS?: number | string
      OFFLINE_BACKGROUND_SYNC_REQUIRE_UNMETERED?: boolean | string
      OFFLINE_BACKGROUND_SYNC_REQUIRE_CHARGING?: boolean | string
      BACKEND_MERGE_ANALYSIS_ENABLED?: boolean | string
      BACKEND_MERGE_APPLY_ENABLED?: boolean | string
      NOTIFICATIONS_ENABLED?: boolean | string
      NOTIFICATIONS_SSE_ENABLED?: boolean | string
      NOTIFICATION_PREFERENCES_ENABLED?: boolean | string
      WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED?: boolean | string
      MFA_UI_ENABLED?: boolean | string
      ADMIN_UI_ENABLED?: boolean | string
      ADMIN_UI_DEV_OPEN?: boolean | string
      ENTERPRISE_ADMIN_WRITE_ENABLED?: boolean | string
      ENTERPRISE_ADMIN_APPROVALS_ENABLED?: boolean | string
      AUDIT_API_MODE?: 'mock' | 'real' | string
    }
  }
}

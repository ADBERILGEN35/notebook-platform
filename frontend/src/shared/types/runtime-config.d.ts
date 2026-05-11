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
      WORKSPACE_NOTIFICATION_POLICIES_ENABLED?: boolean | string
      MFA_UI_ENABLED?: boolean | string
      ADMIN_UI_ENABLED?: boolean | string
      ADMIN_UI_DEV_OPEN?: boolean | string
      ENTERPRISE_ADMIN_WRITE_ENABLED?: boolean | string
      ENTERPRISE_ADMIN_APPROVALS_ENABLED?: boolean | string
      ENTERPRISE_GITOPS_PR_ENABLED?: boolean | string
      /** Faz 87: show GitOps actions for approved RBAC role change requests (requires enterprise GitOps UI). */
      GITOPS_RBAC_ROLE_REQUESTS_ENABLED?: boolean | string
      ADMIN_RBAC_UI_ENABLED?: boolean | string
      ADMIN_RBAC_ROLE_REQUESTS_ENABLED?: boolean | string
      ADMIN_RBAC_OVERRIDES_STATUS_ENABLED?: boolean | string
      ADMIN_RBAC_OVERRIDES_RELOAD_ENABLED?: boolean | string
      NOTIFICATION_ANALYTICS_UI_ENABLED?: boolean | string
      NOTIFICATION_DEAD_LETTER_UI_ENABLED?: boolean | string
      NOTIFICATION_RETENTION_UI_ENABLED?: boolean | string
      NOTIFICATION_RETENTION_PURGE_UI_ENABLED?: boolean | string
      NOTIFICATION_LEGAL_HOLD_UI_ENABLED?: boolean | string
      AUDIT_API_MODE?: 'mock' | 'real' | string
    }
  }
}

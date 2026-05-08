export {}

declare global {
  interface Window {
    __NOTEBOOK_CONFIG__?: {
      API_BASE_URL?: string
      AUTH_TRANSPORT?: 'bearer' | 'cookie' | 'dual'
      NOTIFICATIONS_ENABLED?: boolean | string
      ADMIN_UI_ENABLED?: boolean | string
      ADMIN_UI_DEV_OPEN?: boolean | string
      AUDIT_API_MODE?: 'mock' | 'real' | string
    }
  }
}

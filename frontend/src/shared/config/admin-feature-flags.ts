export type AuditApiMode = 'mock' | 'real'

/** Runtime config may stringify booleans in HTML; allow `boolean` for type safety. */
const parseBool = (raw: string | boolean | undefined, defaultValue = false): boolean => {
  if (raw === undefined || raw === null || raw === '') return defaultValue
  if (typeof raw === 'boolean') return raw
  return String(raw).toLowerCase() === 'true' || raw === '1'
}

export const isAdminUiEnabled = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.ADMIN_UI_ENABLED ?? import.meta.env.VITE_ADMIN_UI_ENABLED,
    false,
  )

export const isAdminUiDevOpen = (): boolean =>
  parseBool(
    window.__NOTEBOOK_CONFIG__?.ADMIN_UI_DEV_OPEN ?? import.meta.env.VITE_ADMIN_UI_DEV_OPEN,
    false,
  )

export const getAuditApiMode = (): AuditApiMode => {
  const raw =
    window.__NOTEBOOK_CONFIG__?.AUDIT_API_MODE ?? import.meta.env.VITE_AUDIT_API_MODE ?? 'mock'
  const normalized = String(raw).toLowerCase()
  return normalized === 'real' ? 'real' : 'mock'
}

const parseBool = (raw: string | boolean | undefined, defaultValue = false): boolean => {
  if (raw === undefined || raw === null || raw === '') return defaultValue
  if (typeof raw === 'boolean') return raw
  return String(raw).toLowerCase() === 'true' || raw === '1'
}

export const isSsoEnabled = (): boolean =>
  parseBool(window.__NOTEBOOK_CONFIG__?.SSO_ENABLED ?? import.meta.env.VITE_SSO_ENABLED, false)

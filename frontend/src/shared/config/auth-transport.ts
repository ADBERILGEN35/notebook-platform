export type AuthTransport = 'bearer' | 'cookie' | 'dual'

const normalizeAuthTransport = (raw: string | undefined): AuthTransport => {
  const value = (raw || '').trim().toLowerCase()
  if (value === 'cookie' || value === 'dual') {
    return value
  }
  return 'bearer'
}

export const getAuthTransport = (): AuthTransport =>
  normalizeAuthTransport(window.__NOTEBOOK_CONFIG__?.AUTH_TRANSPORT || import.meta.env.VITE_AUTH_TRANSPORT)

export const isCookieMode = (): boolean => {
  const transport = getAuthTransport()
  return transport === 'cookie' || transport === 'dual'
}

export function isWebAuthnSupported(): boolean {
  if (typeof window === 'undefined') return false
  const hasApi = typeof window.PublicKeyCredential !== 'undefined'
  return hasApi && window.isSecureContext
}

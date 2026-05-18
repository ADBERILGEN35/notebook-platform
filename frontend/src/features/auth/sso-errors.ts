const SSO_SAFE_MESSAGES: Record<string, string> = {
  SSO_STATE_INVALID: 'Sign-in could not be completed. Please try again.',
  SSO_STATE_EXPIRED: 'Your sign-in session expired. Please try again.',
  SSO_NONCE_MISMATCH: 'Sign-in verification failed. Please try again.',
}

export function ssoErrorMessage(code: string | null | undefined): string {
  if (!code) return 'Sign-in could not be completed.'
  return SSO_SAFE_MESSAGES[code] ?? 'Sign-in could not be completed. Please try again.'
}

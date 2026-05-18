const MFA_SESSION_KEY = 'np_mfa_session_id'

export function setMfaSessionId(sessionId: string): void {
  sessionStorage.setItem(MFA_SESSION_KEY, sessionId)
}

export function getMfaSessionId(): string | null {
  return sessionStorage.getItem(MFA_SESSION_KEY)
}

export function clearMfaSessionId(): void {
  sessionStorage.removeItem(MFA_SESSION_KEY)
}

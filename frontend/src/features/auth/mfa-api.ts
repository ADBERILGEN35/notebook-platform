import { apiRequest } from '../../shared/api/api-client'

export interface MfaSettingsResponse {
  mfaEnabled: boolean
  webauthnEnabled: boolean
  backupCodesEnabled: boolean
  recoveryCodesRemaining: number
  activeCredentialCount: number
  mfaRequired: boolean
  availableMethods: string[]
}

export async function getMfaSettings(): Promise<MfaSettingsResponse> {
  return apiRequest<MfaSettingsResponse>('/auth/mfa/settings', { method: 'GET' })
}

export async function registrationOptions() {
  return apiRequest<{
    challenge: string
    rpId: string
    rpName: string
    userId: string
    userName: string
    userDisplayName: string
    excludeCredentials: Array<{ id: string; type: string }>
    userVerification: string
  }>('/auth/mfa/webauthn/registration/options', { method: 'POST', body: JSON.stringify({}) })
}

export async function registrationVerify(payload: {
  credentialId: string
  publicKeyCose: string
  challenge: string
  origin: string
  signCount?: number
  name?: string
}) {
  return apiRequest('/auth/mfa/webauthn/registration/verify', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function authenticationOptions(mfaSessionId: string) {
  return apiRequest<{
    mfaSessionId: string
    challenge: string
    rpId: string
    allowCredentialIds: string[]
    userVerification: string
  }>('/auth/mfa/webauthn/authentication/options', {
    method: 'POST',
    body: JSON.stringify({ mfaSessionId }),
  })
}

export async function authenticationVerify(payload: {
  mfaSessionId: string
  credentialId: string
  challenge: string
  origin: string
}) {
  return apiRequest('/auth/mfa/webauthn/authentication/verify', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function generateRecoveryCodes(acknowledgeReplace = false) {
  return apiRequest<{ codes: string[] }>('/auth/mfa/recovery-codes/generate', {
    method: 'POST',
    body: JSON.stringify({ acknowledgeReplace }),
  })
}

export async function verifyRecoveryCode(payload: { mfaSessionId: string; recoveryCode: string }) {
  return apiRequest('/auth/mfa/recovery-codes/verify', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

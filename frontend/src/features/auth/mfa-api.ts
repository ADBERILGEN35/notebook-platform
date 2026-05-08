import { apiRequest } from '../../shared/api/api-client'

export interface MfaSettingsResponse {
  mfaEnabled: boolean
  webauthnEnabled: boolean
  backupCodesEnabled: boolean
  mfaRequired: boolean
  availableMethods: string[]
}

export async function getMfaSettings(): Promise<MfaSettingsResponse> {
  return apiRequest<MfaSettingsResponse>('/auth/mfa/settings', { method: 'GET' })
}

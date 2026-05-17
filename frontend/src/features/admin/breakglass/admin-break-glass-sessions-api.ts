import { apiRequest } from '../../../shared/api/api-client'

export type BreakGlassSessionRow = {
  eventId: string
  sessionId: string
  revokeRef: string
  jtiMasked: string
  mode: string
  actorLabel: string
  status: string
  tokenStatus: 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'UNKNOWN' | string
  issuedAt: string
  expiresAt: string
  revocationSource?: string
}

export type BreakGlassSessionListResponse = {
  items: BreakGlassSessionRow[]
  activeCount: number
}

export async function listBreakGlassActiveSessions(): Promise<BreakGlassSessionListResponse> {
  return apiRequest<BreakGlassSessionListResponse>('/admin/security/break-glass/sessions', {
    method: 'GET',
  })
}

export async function revokeBreakGlassSession(
  revokeRef: string,
  body: { reason: string },
): Promise<{ revoked: boolean; alreadyExpired: boolean; alreadyRevoked: boolean; sessionId: string }> {
  return apiRequest(`/admin/security/break-glass/sessions/${encodeURIComponent(revokeRef)}/revoke`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function revokeAllBreakGlassActiveSessions(body: {
  reason: string
}): Promise<{ revokedCount: number; alreadyRevokedCount: number; expiredCount: number }> {
  return apiRequest('/admin/security/break-glass/sessions/revoke-all-active', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

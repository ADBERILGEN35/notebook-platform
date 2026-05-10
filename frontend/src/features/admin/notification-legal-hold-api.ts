import { apiRequest } from '../../shared/api/api-client'

export type LegalHoldScope =
  | 'ALL_NOTIFICATION_RETENTION'
  | 'FANOUT_OUTBOX'
  | 'DEAD_LETTER_REQUEUE_REQUESTS'
  | 'ANALYTICS'
  | 'DIGEST_ITEMS'
  | 'EMAIL_NOTIFICATIONS'

export type LegalHoldResponse = {
  id: string
  holdKey: string
  scope: string
  status: string
  createdAt: string
  expiresAt: string | null
  createdByUserId: string
  createdByEmail: string | null
  releasedAt: string | null
  releasedByUserId: string | null
}

export type LegalHoldListResponse = {
  holds: LegalHoldResponse[]
}

export async function fetchLegalHolds(status?: 'ACTIVE' | 'RELEASED'): Promise<LegalHoldListResponse> {
  const q = status ? `?status=${encodeURIComponent(status)}` : ''
  return apiRequest<LegalHoldListResponse>(`/admin/notifications/legal-holds${q}`, { method: 'GET' })
}

export async function createLegalHold(body: {
  holdKey: string
  scope: LegalHoldScope
  reason: string
  expiresAt?: string | null
}): Promise<LegalHoldResponse> {
  return apiRequest<LegalHoldResponse>('/admin/notifications/legal-holds', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function releaseLegalHold(id: string, reason: string): Promise<LegalHoldResponse> {
  return apiRequest<LegalHoldResponse>(`/admin/notifications/legal-holds/${encodeURIComponent(id)}/release`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
  })
}

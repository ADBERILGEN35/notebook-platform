import { apiRequest } from '../../../shared/api/api-client'

export type RotationEventStatus = 'REQUIRED' | 'ACKNOWLEDGED' | 'VERIFIED' | 'CLOSED' | 'EXPIRED'

export type RotationEventRow = {
  id: string
  credentialMode: string
  status: RotationEventStatus | string
  oldFingerprint?: string | null
  newFingerprint?: string | null
  triggeredByEventId?: string | null
  triggeredBySessionId?: string | null
  requiredAt: string
  acknowledgedAt?: string | null
  verifiedAt?: string | null
  closedAt?: string | null
}

export type RotationEventDetail = RotationEventRow & {
  acknowledgedByUserId?: string | null
  verifiedByUserId?: string | null
  reason?: string | null
}

export type RotationEventListResponse = {
  items: RotationEventRow[]
  total: number
  openCount: number
  requiredCount: number
}

export async function listBreakGlassRotationEvents(params: {
  status?: string
  page?: number
  size?: number
}): Promise<RotationEventListResponse> {
  const sp = new URLSearchParams()
  if (params.status) sp.set('status', params.status)
  sp.set('page', String(params.page ?? 0))
  sp.set('size', String(params.size ?? 25))
  return apiRequest<RotationEventListResponse>(
    `/admin/break-glass/rotation-events?${sp.toString()}`,
    { method: 'GET' },
  )
}

export async function getBreakGlassRotationEvent(id: string): Promise<RotationEventDetail> {
  return apiRequest<RotationEventDetail>(
    `/admin/break-glass/rotation-events/${encodeURIComponent(id)}`,
    { method: 'GET' },
  )
}

export async function acknowledgeBreakGlassRotationEvent(
  id: string,
  body: { reason: string },
): Promise<RotationEventDetail> {
  return apiRequest<RotationEventDetail>(
    `/admin/break-glass/rotation-events/${encodeURIComponent(id)}/acknowledge`,
    { method: 'POST', body: JSON.stringify(body) },
  )
}

export async function verifyBreakGlassRotationEvent(
  id: string,
  body: { reason: string },
): Promise<RotationEventDetail> {
  return apiRequest<RotationEventDetail>(
    `/admin/break-glass/rotation-events/${encodeURIComponent(id)}/verify`,
    { method: 'POST', body: JSON.stringify(body) },
  )
}

export async function closeBreakGlassRotationEvent(
  id: string,
  body: { reason: string },
): Promise<RotationEventDetail> {
  return apiRequest<RotationEventDetail>(
    `/admin/break-glass/rotation-events/${encodeURIComponent(id)}/close`,
    { method: 'POST', body: JSON.stringify(body) },
  )
}

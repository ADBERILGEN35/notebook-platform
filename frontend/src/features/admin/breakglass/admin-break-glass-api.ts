import { apiRequest } from '../../../shared/api/api-client'

export type BreakGlassEventRow = {
  id: string
  sessionId: string
  mode: string
  actorLabel: string
  status: string
  rotationRequired: boolean
  issuedAt: string
  expiresAt: string
  reviewedAt?: string | null
}

export type BreakGlassEventListResponse = {
  items: BreakGlassEventRow[]
  total: number
  overdueCount: number
}

export type BreakGlassEventDetail = BreakGlassEventRow & {
  reasonSummary?: string | null
  reviewedByUserId?: string | null
  reviewDecision?: string | null
  reviewReason?: string | null
}

export async function listBreakGlassEvents(params: {
  status?: string
  mode?: string
  page?: number
  size?: number
}): Promise<BreakGlassEventListResponse> {
  const sp = new URLSearchParams()
  if (params.status) sp.set('status', params.status)
  if (params.mode) sp.set('mode', params.mode)
  sp.set('page', String(params.page ?? 0))
  sp.set('size', String(params.size ?? 25))
  return apiRequest<BreakGlassEventListResponse>(`/admin/break-glass/events?${sp.toString()}`, {
    method: 'GET',
  })
}

export async function getBreakGlassEvent(id: string): Promise<BreakGlassEventDetail> {
  return apiRequest<BreakGlassEventDetail>(`/admin/break-glass/events/${encodeURIComponent(id)}`, {
    method: 'GET',
  })
}

export async function reviewBreakGlassEvent(
  id: string,
  body: { decision: 'APPROVE' | 'REJECT' | 'CLOSE'; reason: string; rotationRunbookAcknowledged: boolean },
): Promise<BreakGlassEventDetail> {
  return apiRequest<BreakGlassEventDetail>(`/admin/break-glass/events/${encodeURIComponent(id)}/review`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

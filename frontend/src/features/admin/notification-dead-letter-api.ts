import { apiRequest } from '../../shared/api/api-client'

export type DeadLetterListItem = {
  id: string
  source: string
  eventType: string
  recipientUserIdHash: string
  status: string
  attemptCount: number
  requeueCount: number
  lastErrorCode: string
  lastErrorSummary: string
  createdAt: string
  updatedAt: string
  deadAt: string | null
}

export type DeadLetterListResponse = {
  items: DeadLetterListItem[]
  page: number
  size: number
  totalElements: number
}

export type RequeueDryRunResponse = {
  id: string
  canRequeue: boolean
  source: string
  impact: { severity: string; duplicateRisk: string; reason: string }
  checks: { code: string; passed: boolean }[]
}

export type FanoutRequeueResponse = {
  id: string
  source: string
  status: string
  attemptCount: number
  requeueCount: number
  nextAttemptAt: string
  idempotentReplay: boolean
}

export async function fetchDeadLetterList(params: {
  eventType?: string
  createdFrom?: string
  createdTo?: string
  page?: number
  size?: number
  sort?: string
}): Promise<DeadLetterListResponse> {
  const q = new URLSearchParams({ source: 'fanout', status: 'DEAD' })
  if (params.eventType) q.set('eventType', params.eventType)
  if (params.createdFrom) q.set('createdFrom', params.createdFrom)
  if (params.createdTo) q.set('createdTo', params.createdTo)
  q.set('page', String(params.page ?? 0))
  q.set('size', String(params.size ?? 50))
  if (params.sort) q.set('sort', params.sort)
  return apiRequest<DeadLetterListResponse>(`/admin/notifications/dead-letter?${q.toString()}`, {
    method: 'GET',
  })
}

export async function dryRunDeadLetterRequeue(id: string): Promise<RequeueDryRunResponse> {
  return apiRequest<RequeueDryRunResponse>(
    `/admin/notifications/dead-letter/${encodeURIComponent(id)}/requeue/dry-run`,
    { method: 'POST' },
  )
}

export async function requeueDeadLetter(
  id: string,
  body: { idempotencyKey: string; reason: string },
): Promise<FanoutRequeueResponse> {
  return apiRequest<FanoutRequeueResponse>(
    `/admin/notifications/dead-letter/${encodeURIComponent(id)}/requeue`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    },
  )
}

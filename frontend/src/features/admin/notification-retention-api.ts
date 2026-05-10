import { apiRequest } from '../../shared/api/api-client'

export type RetentionPlanTarget = {
  target: string
  eligibleCount: number
  retention: string
  oldestEligibleAt: string | null
  cutoff: string
  blockedByLegalHold?: boolean
  activeHoldKeys?: string[]
  purgeableCount?: number
  targetWarnings?: string[]
}

export type RetentionPlanResponse = {
  generatedAt: string
  dryRun: boolean
  targets: RetentionPlanTarget[]
  warnings: string[]
}

export type RetentionRunResponse = {
  dryRun: boolean
  target: string
  totalDeleted: number
  deletedByTarget: Record<string, number>
  skippedByLegalHold?: number
  legalHoldKeysBlocking?: string[]
  planSnapshot: RetentionPlanResponse
}

export async function fetchRetentionPlan(dryRun = true): Promise<RetentionPlanResponse> {
  const q = new URLSearchParams({ dryRun: String(dryRun) })
  return apiRequest<RetentionPlanResponse>(`/admin/notifications/retention/plan?${q.toString()}`, {
    method: 'GET',
  })
}

export async function runRetention(body: {
  dryRun: boolean
  target?: string
  reason?: string
}): Promise<RetentionRunResponse> {
  return apiRequest<RetentionRunResponse>('/admin/notifications/retention/run', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

import { z } from 'zod'
import { apiRequest } from '../../shared/api/api-client'

export const retentionTargetSchema = z.object({
  targetKey: z.string(),
  service: z.string(),
  displayName: z.string(),
  description: z.string(),
  dataClass: z.string(),
  defaultRetentionDays: z.number().nullable().optional(),
  legalHoldSupported: z.boolean(),
  destructivePurgeSupported: z.boolean(),
  dryRunSupported: z.boolean(),
  archiveRequiredBeforePurge: z.boolean(),
  riskLevel: z.string(),
  status: z.string(),
})

export const retentionTargetsResponseSchema = z.object({
  generatedAt: z.string(),
  targets: z.array(retentionTargetSchema),
})

export const retentionPlanTargetSchema = z.object({
  targetKey: z.string(),
  service: z.string(),
  status: z.string(),
  defaultRetentionDays: z.number().nullable().optional(),
  eligibleCount: z.number().nullable().optional(),
  purgeableCount: z.number(),
  blockedByLegalHold: z.boolean(),
  riskLevel: z.string(),
  activeHoldKeys: z.array(z.string()),
  warnings: z.array(z.string()),
})

export const retentionPlanResponseSchema = z.object({
  generatedAt: z.string(),
  dryRun: z.boolean(),
  targets: z.array(retentionPlanTargetSchema),
  warnings: z.array(z.string()),
})

export const platformLegalHoldSchema = z.object({
  id: z.string(),
  holdKey: z.string(),
  scope: z.string(),
  scopeRefPresent: z.boolean(),
  status: z.string(),
  createdByUserId: z.string(),
  createdAt: z.string(),
  releasedByUserId: z.string().nullable().optional(),
  releasedAt: z.string().nullable().optional(),
  expiresAt: z.string().nullable().optional(),
})

export const platformLegalHoldListSchema = z.object({
  items: z.array(platformLegalHoldSchema),
})

export type RetentionTargetsResponse = z.infer<typeof retentionTargetsResponseSchema>
export type RetentionPlanResponse = z.infer<typeof retentionPlanResponseSchema>
export type PlatformLegalHoldList = z.infer<typeof platformLegalHoldListSchema>

export async function fetchPlatformRetentionTargets(): Promise<RetentionTargetsResponse> {
  const raw = await apiRequest<unknown>('/admin/retention/platform/targets', { method: 'GET' })
  const parsed = retentionTargetsResponseSchema.safeParse(raw)
  if (!parsed.success) throw new Error('Invalid platform retention targets response')
  return parsed.data
}

export async function fetchPlatformRetentionPlan(): Promise<RetentionPlanResponse> {
  const raw = await apiRequest<unknown>('/admin/retention/platform/plan?dryRun=true', {
    method: 'GET',
  })
  const parsed = retentionPlanResponseSchema.safeParse(raw)
  if (!parsed.success) throw new Error('Invalid platform retention plan response')
  return parsed.data
}

export async function fetchPlatformLegalHolds(): Promise<PlatformLegalHoldList> {
  const raw = await apiRequest<unknown>('/admin/retention/platform/legal-holds', { method: 'GET' })
  const parsed = platformLegalHoldListSchema.safeParse(raw)
  if (!parsed.success) throw new Error('Invalid platform legal holds response')
  return parsed.data
}

export async function createPlatformLegalHold(body: {
  holdKey: string
  scope: string
  scopeRefId?: string
  reason: string
  expiresAt?: string
}) {
  return apiRequest<unknown>('/admin/retention/platform/legal-holds', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function releasePlatformLegalHold(id: string, reason: string) {
  return apiRequest<unknown>(`/admin/retention/platform/legal-holds/${encodeURIComponent(id)}/release`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
  })
}

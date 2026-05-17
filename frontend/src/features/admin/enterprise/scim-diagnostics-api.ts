import { z } from 'zod'
import { apiRequest } from '../../../shared/api/api-client'

export const scimProviderStatusSchema = z.object({
  type: z.string(),
  deltaSyncEnabled: z.boolean(),
  deltaSyncMode: z.string(),
  bulkSupported: z.boolean(),
  filteringSupported: z.boolean(),
  patchSupported: z.boolean(),
  nestedGroupsSupported: z.boolean(),
  rateLimitAware: z.boolean(),
  maxPageSize: z.number(),
  lastSyncStatus: z.string(),
  warnings: z.array(z.string()),
})

export const scimCheckpointSchema = z.object({
  id: z.string(),
  provider: z.string(),
  resourceType: z.string(),
  syncMode: z.string(),
  checkpointPresent: z.boolean(),
  lastSuccessfulSyncAt: z.string().nullable().optional(),
  lastAttemptAt: z.string().nullable().optional(),
  status: z.string(),
  updatedAt: z.string().nullable().optional(),
})

export const scimSyncRunSchema = z.object({
  id: z.string(),
  provider: z.string(),
  resourceType: z.string(),
  syncMode: z.string(),
  status: z.string(),
  startedAt: z.string(),
  completedAt: z.string().nullable().optional(),
  processedCount: z.number(),
  createdCount: z.number(),
  updatedCount: z.number(),
  deprovisionedCount: z.number(),
  skippedCount: z.number(),
  errorCount: z.number(),
  lastErrorCode: z.string().nullable().optional(),
  lastErrorSummary: z.string().nullable().optional(),
  requestId: z.string().nullable().optional(),
})

export const scimCompatibilityStatusSchema = z.object({
  scimProvider: scimProviderStatusSchema,
  checkpoints: z.array(scimCheckpointSchema),
  lastRun: scimSyncRunSchema.nullable().optional(),
})

export const scimSyncRunPageSchema = z.object({
  items: z.array(scimSyncRunSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
})

export type ScimCompatibilityStatus = z.infer<typeof scimCompatibilityStatusSchema>
export type ScimSyncRunPage = z.infer<typeof scimSyncRunPageSchema>

export async function fetchScimCompatibilityStatus(): Promise<ScimCompatibilityStatus> {
  const raw = await apiRequest<unknown>('/admin/identity/scim/compatibility/status', { method: 'GET' })
  const parsed = scimCompatibilityStatusSchema.safeParse(raw)
  if (!parsed.success) throw new Error('Invalid SCIM compatibility status response')
  return parsed.data
}

export async function fetchScimSyncRuns(): Promise<ScimSyncRunPage> {
  const raw = await apiRequest<unknown>('/admin/identity/scim/sync-runs?page=0&size=10', { method: 'GET' })
  const parsed = scimSyncRunPageSchema.safeParse(raw)
  if (!parsed.success) throw new Error('Invalid SCIM sync runs response')
  return parsed.data
}

export const scimDeltaReadinessSchema = z.object({
  providerType: z.string(),
  deltaPocEnabled: z.boolean(),
  dryRunOnly: z.boolean(),
  selectedStrategy: z.string(),
  deltaSource: z.string(),
  supportsFiltering: z.boolean(),
  supportsPagination: z.boolean(),
  supportsPatch: z.boolean(),
  supportsRetryAfter: z.boolean(),
  capabilityAligned: z.boolean(),
  deprovisionSemantics: z.string(),
  lastCheckpoint: z.object({
    resourceType: z.string(),
    checkpointPresent: z.boolean(),
    status: z.string(),
    lastSuccessfulSyncAt: z.string().nullable().optional(),
  }),
  lastDryRunStatus: z.string(),
  remoteFetchEnabled: z.boolean(),
  remoteFetchConfigured: z.boolean(),
  remoteFetchAttempted: z.boolean(),
  fetchedResourceCount: z.number(),
  pagesObserved: z.number(),
  nextCursorPresent: z.boolean(),
  remoteMultiPageEnabled: z.boolean(),
  stoppedReason: z.string(),
  pageLimitReached: z.boolean(),
  resourceLimitReached: z.boolean(),
  rateLimitAware: z.boolean(),
  retryAfterObserved: z.boolean(),
  retryAfterSeconds: z.number().nullable().optional(),
  retryAfterCapped: z.boolean().nullable().optional(),
  nextRecommendedAttemptAt: z.string().nullable().optional(),
  providerErrorClass: z.string(),
  backoffBaseSeconds: z.number(),
  httpTimeoutMs: z.number(),
  warnings: z.array(z.string()),
})

export type ScimDeltaReadiness = z.infer<typeof scimDeltaReadinessSchema>

export async function fetchScimDeltaReadiness(): Promise<ScimDeltaReadiness> {
  const raw = await apiRequest<unknown>('/admin/identity/scim/delta/readiness', { method: 'GET' })
  const parsed = scimDeltaReadinessSchema.safeParse(raw)
  if (!parsed.success) throw new Error('Invalid SCIM delta readiness response')
  return parsed.data
}

import { describe, expect, it } from 'vitest'
import {
  scimCompatibilityStatusSchema,
  scimDeltaReadinessSchema,
  scimSyncRunPageSchema,
} from './scim-diagnostics-api'

describe('scim diagnostics schemas', () => {
  it('parses compatibility status without token or raw payload fields', () => {
    const parsed = scimCompatibilityStatusSchema.safeParse({
      scimProvider: {
        type: 'okta',
        deltaSyncEnabled: false,
        deltaSyncMode: 'diagnostic',
        bulkSupported: true,
        filteringSupported: true,
        patchSupported: true,
        nestedGroupsSupported: false,
        rateLimitAware: true,
        maxPageSize: 100,
        lastSyncStatus: 'IDLE',
        warnings: ['SCIM_TOKEN_MISSING'],
      },
      checkpoints: [
        {
          id: '00000000-0000-0000-0000-000000000001',
          provider: 'okta',
          resourceType: 'USER',
          syncMode: 'DELTA',
          checkpointPresent: true,
          lastSuccessfulSyncAt: '2026-05-12T10:00:00Z',
          lastAttemptAt: '2026-05-12T10:00:00Z',
          status: 'IDLE',
          updatedAt: '2026-05-12T10:00:00Z',
        },
      ],
      lastRun: null,
    })

    expect(parsed.success).toBe(true)
    expect(JSON.stringify(parsed.success ? parsed.data : {})).not.toContain('token')
    expect(JSON.stringify(parsed.success ? parsed.data : {})).not.toContain('rawPayload')
  })

  it('parses sync runs table payload', () => {
    const parsed = scimSyncRunPageSchema.safeParse({
      items: [
        {
          id: '00000000-0000-0000-0000-000000000002',
          provider: 'generic',
          resourceType: 'GROUP',
          syncMode: 'FULL',
          status: 'COMPLETED',
          startedAt: '2026-05-12T10:00:00Z',
          completedAt: '2026-05-12T10:01:00Z',
          processedCount: 10,
          createdCount: 1,
          updatedCount: 2,
          deprovisionedCount: 0,
          skippedCount: 7,
          errorCount: 0,
          lastErrorCode: null,
          lastErrorSummary: null,
          requestId: 'req-1',
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    })

    expect(parsed.success).toBe(true)
    expect(parsed.success ? parsed.data.items[0].processedCount : 0).toBe(10)
  })

  it('parses delta readiness without token or raw payload', () => {
    const parsed = scimDeltaReadinessSchema.safeParse({
      providerType: 'okta',
      deltaPocEnabled: true,
      dryRunOnly: true,
      selectedStrategy: 'LAST_MODIFIED_FILTER',
      deltaSource: 'lastModified-filter-poc:okta',
      supportsFiltering: true,
      supportsPagination: true,
      supportsPatch: true,
      supportsRetryAfter: true,
      capabilityAligned: true,
      deprovisionSemantics: 'missing from delta does not deprovision',
      lastCheckpoint: {
        resourceType: 'USER',
        checkpointPresent: false,
        status: 'IDLE',
        lastSuccessfulSyncAt: null,
      },
      lastDryRunStatus: 'COMPLETED',
      remoteFetchEnabled: false,
      remoteFetchConfigured: false,
      remoteFetchAttempted: false,
      fetchedResourceCount: 0,
      pagesObserved: 0,
      nextCursorPresent: false,
      remoteMultiPageEnabled: false,
      stoppedReason: 'SINGLE_PAGE_ONLY',
      pageLimitReached: false,
      resourceLimitReached: false,
      rateLimitAware: true,
      retryAfterObserved: false,
      retryAfterSeconds: null,
      retryAfterCapped: false,
      nextRecommendedAttemptAt: null,
      providerErrorClass: 'NONE',
      backoffBaseSeconds: 30,
      httpTimeoutMs: 3000,
      warnings: [
        'SCIM_DELTA_DRY_RUN_ONLY',
        'SCIM_DELTA_MISSING_USER_IGNORED',
        'SCIM_DELTA_REMOTE_FETCH_DISABLED',
      ],
    })

    expect(parsed.success).toBe(true)
    expect(JSON.stringify(parsed.success ? parsed.data : {})).not.toMatch(/token|Bearer|rawPayload/i)
  })
})

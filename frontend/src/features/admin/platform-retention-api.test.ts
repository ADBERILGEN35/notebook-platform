import { describe, expect, it } from 'vitest'
import {
  platformLegalHoldListSchema,
  retentionPlanResponseSchema,
  retentionTargetsResponseSchema,
} from './platform-retention-api'

describe('platform retention schemas', () => {
  it('parses retention target inventory without content fields', () => {
    const parsed = retentionTargetsResponseSchema.safeParse({
      generatedAt: '2026-05-13T10:00:00Z',
      targets: [
        {
          targetKey: 'content.note_versions',
          service: 'content-service',
          displayName: 'Note versions',
          description: 'Historical note versions.',
          dataClass: 'CONTENT',
          defaultRetentionDays: 365,
          legalHoldSupported: true,
          destructivePurgeSupported: false,
          dryRunSupported: true,
          archiveRequiredBeforePurge: true,
          riskLevel: 'HIGH',
          status: 'INVENTORY_ONLY',
        },
      ],
    })
    expect(parsed.success).toBe(true)
    expect(JSON.stringify(parsed.success ? parsed.data : {})).not.toContain('noteBody')
  })

  it('parses dry-run plan with legal hold block', () => {
    const parsed = retentionPlanResponseSchema.safeParse({
      generatedAt: '2026-05-13T10:00:00Z',
      dryRun: true,
      warnings: ['dry-run only'],
      targets: [
        {
          targetKey: 'audit.events',
          service: 'identity-service',
          status: 'INVENTORY_ONLY',
          defaultRetentionDays: 365,
          eligibleCount: null,
          purgeableCount: 0,
          blockedByLegalHold: true,
          riskLevel: 'CRITICAL',
          activeHoldKeys: ['hold-1'],
          warnings: ['Target is blocked by active platform legal hold.'],
        },
      ],
    })
    expect(parsed.success).toBe(true)
    expect(parsed.success ? parsed.data.targets[0].blockedByLegalHold : false).toBe(true)
  })

  it('accepts content-enriched plan targets with cutoff and DRY_RUN_READY status', () => {
    const parsed = retentionPlanResponseSchema.safeParse({
      generatedAt: '2026-05-13T10:00:00Z',
      dryRun: true,
      warnings: ['PLATFORM_RETENTION_CONTENT_PLAN_INCLUDED'],
      targets: [
        {
          targetKey: 'content.note_versions',
          service: 'content-service',
          status: 'DRY_RUN_READY',
          defaultRetentionDays: 365,
          eligibleCount: 1200,
          purgeableCount: 1200,
          blockedByLegalHold: false,
          riskLevel: 'HIGH',
          activeHoldKeys: [],
          warnings: [],
          cutoff: '2025-05-13T10:00:00Z',
        },
      ],
    })
    expect(parsed.success).toBe(true)
    if (parsed.success) {
      expect(parsed.data.targets[0].eligibleCount).toBe(1200)
      expect(parsed.data.targets[0].cutoff).toBe('2025-05-13T10:00:00Z')
      expect(JSON.stringify(parsed.data)).not.toContain('contentBlocks')
      expect(JSON.stringify(parsed.data)).not.toContain('noteBody')
      expect(JSON.stringify(parsed.data)).not.toContain('email')
    }
  })

  it('parses legal hold list without reasons', () => {
    const parsed = platformLegalHoldListSchema.safeParse({
      items: [
        {
          id: '00000000-0000-0000-0000-000000000001',
          holdKey: 'case-2026-01',
          scope: 'ALL_PLATFORM',
          scopeRefPresent: false,
          status: 'ACTIVE',
          createdByUserId: '00000000-0000-0000-0000-000000000002',
          createdAt: '2026-05-13T10:00:00Z',
          releasedByUserId: null,
          releasedAt: null,
          expiresAt: null,
        },
      ],
    })
    expect(parsed.success).toBe(true)
    expect(JSON.stringify(parsed.success ? parsed.data : {})).not.toContain('reason')
  })
})

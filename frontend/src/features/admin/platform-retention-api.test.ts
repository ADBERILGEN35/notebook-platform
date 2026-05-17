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

  it('parses plan with optional service summaries and no raw domain data', () => {
    const parsed = retentionPlanResponseSchema.safeParse({
      generatedAt: '2026-05-16T10:00:00Z',
      dryRun: true,
      warnings: ['PLATFORM_RETENTION_NOTIFICATION_PLAN_INCLUDED'],
      targets: [],
      serviceSummaries: [
        {
          service: 'notification-service',
          dataClass: 'NOTIFICATION',
          status: 'PARTIAL',
          totalTargets: 6,
          dryRunReadyTargets: 5,
          inventoryOnlyTargets: 1,
          unavailableTargets: 0,
          blockedTargets: 1,
          cappedTargets: 0,
          warningCount: 2,
          warnings: [
            'NOTIFICATION_RETENTION_LEGAL_HOLD_BLOCKED',
            'PLATFORM_RETENTION_NOTIFICATION_PLAN_INCLUDED',
          ],
        },
      ],
    })
    expect(parsed.success).toBe(true)
    if (parsed.success) {
      expect(parsed.data.serviceSummaries?.[0].status).toBe('PARTIAL')
      expect(parsed.data.serviceSummaries?.[0].blockedTargets).toBe(1)
      const serialized = JSON.stringify(parsed.data)
      expect(serialized).not.toContain('noteBody')
      expect(serialized).not.toContain('contentBlocks')
      expect(serialized).not.toContain('recipient')
      expect(serialized).not.toContain('email')
    }
  })

  it('parses plan without service summaries (backward compatible)', () => {
    const parsed = retentionPlanResponseSchema.safeParse({
      generatedAt: '2026-05-16T10:00:00Z',
      dryRun: true,
      warnings: [],
      targets: [],
    })
    expect(parsed.success).toBe(true)
    expect(parsed.success ? parsed.data.serviceSummaries : 'x').toBeUndefined()
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

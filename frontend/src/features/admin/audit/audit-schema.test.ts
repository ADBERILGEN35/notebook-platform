import { describe, expect, it } from 'vitest'
import { auditQueryFiltersSchema, defaultAuditFilters } from './audit-schema'

describe('audit filters schema', () => {
  it('validates sort patterns', () => {
    const ok = auditQueryFiltersSchema.safeParse({ ...defaultAuditFilters(), sort: 'eventType,asc' })
    expect(ok.success).toBe(true)
  })

  it('rejects reversed date range', () => {
    const parsed = auditQueryFiltersSchema.safeParse({
      ...defaultAuditFilters(),
      createdFrom: '2026-06-01T00:00:00Z',
      createdTo: '2026-05-01T00:00:00Z',
    })
    expect(parsed.success).toBe(false)
  })
})

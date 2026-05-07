import { describe, expect, it } from 'vitest'
import { auditFiltersFromUrlParams, auditFiltersToUrlParams } from './audit-url-state'
import { defaultAuditFilters } from './audit-schema'

describe('audit URL state', () => {
  it('round-trips query string', () => {
    const filters = { ...defaultAuditFilters('identity'), page: 2, size: 50, eventType: 'X' }
    const sp = auditFiltersToUrlParams(filters)
    const back = auditFiltersFromUrlParams(sp)
    expect(back.filters.eventType).toBe('X')
    expect(back.filters.page).toBe(2)
    expect(back.parseWarning).toBeNull()
  })
})

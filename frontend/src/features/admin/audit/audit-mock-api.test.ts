import { describe, expect, it } from 'vitest'
import { fetchAuditEventsMock, MOCK_AUDIT_EVENTS } from './audit-mock-api'
import { defaultAuditFilters } from './audit-schema'

describe('audit mock api', () => {
  it('returns page shape', async () => {
    const page = await fetchAuditEventsMock(defaultAuditFilters('workspace'))
    expect(page.items.some((row) => row.eventType === 'WORKSPACE_CREATED')).toBe(true)
  })

  it('filters identity event types', async () => {
    const page = await fetchAuditEventsMock({
      ...defaultAuditFilters('identity'),
      eventType: 'REFRESH_TOKEN_ROTATED',
      size: 25,
    })
    expect(page.totalElements).toBe(1)
  })

  it('includes synthetic identity fixtures for pagination', () => {
    expect(MOCK_AUDIT_EVENTS.filter((e) => e.source === 'identity').length).toBeGreaterThanOrEqual(28)
  })
})

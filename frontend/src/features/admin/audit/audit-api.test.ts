import { describe, expect, it, vi } from 'vitest'
import { fetchAuditEventsReal } from './audit-api'
import { defaultAuditFilters } from './audit-schema'

const apiRequestMock = vi.hoisted(() => vi.fn())

vi.mock('../../../shared/api/api-client', () => ({
  apiRequest: apiRequestMock,
}))

describe('audit api', () => {
  it('calls gateway admin audit endpoint in real mode', async () => {
    apiRequestMock.mockResolvedValueOnce({
      items: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
      hasPrevious: false,
    })

    await fetchAuditEventsReal(defaultAuditFilters('workspace'))
    expect(apiRequestMock).toHaveBeenCalledWith(
      expect.stringContaining('/admin/audit-events?source=workspace'),
      { method: 'GET' },
    )
  })
})

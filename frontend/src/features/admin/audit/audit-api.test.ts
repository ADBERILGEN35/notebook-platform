import { describe, expect, it, vi, beforeEach } from 'vitest'
import { exportAuditEvents, fetchAuditEventsReal } from './audit-api'
import { defaultAuditFilters } from './audit-schema'

const apiRequestMock = vi.hoisted(() => vi.fn())

vi.mock('../../../shared/api/api-client', () => ({
  apiRequest: apiRequestMock,
}))
vi.mock('../../auth/auth-store', () => ({
  useAuthStore: { getState: () => ({ accessToken: 'token' }) },
}))
vi.mock('../../../shared/config/auth-transport', () => ({
  getAuthTransport: () => 'bearer',
  isCookieMode: () => false,
}))

describe('audit api', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

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

  it('exports audit events through gateway export endpoint', async () => {
    const blob = new Blob(['id,source\n1,identity'])
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      headers: {
        get: (name: string) =>
          name === 'Content-Disposition' ? 'attachment; filename="audit-identity.csv"' : 'text/csv',
      },
      blob: async () => blob,
    } as unknown as Response)

    const result = await exportAuditEvents(defaultAuditFilters('identity'), 'csv')
    expect(result.fileName).toContain('audit-identity.csv')
  })
})

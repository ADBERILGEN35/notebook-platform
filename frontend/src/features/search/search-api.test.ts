import { describe, expect, it, vi } from 'vitest'
import { searchNotes } from './search-api'

describe('search api', () => {
  it('calls search endpoint with query params', async () => {
    const mockFetch = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false }), { status: 200 }))
    await searchNotes('workspace-1', 'incident', 1, 10)
    expect(String(mockFetch.mock.calls[0][0])).toContain('/search/notes?workspaceId=workspace-1&q=incident&page=1&size=10')
  })
})


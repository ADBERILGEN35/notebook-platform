import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, ApiError } from './api-client'
import { useAuthStore } from '../../features/auth/auth-store'
import { useWorkspaceStore } from '../../features/workspaces/workspace-store'

describe('api client', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.getState().clearSession()
    useWorkspaceStore.getState().setActiveWorkspaceId(null)
    vi.restoreAllMocks()
  })

  it('adds authorization and workspace headers', async () => {
    useAuthStore.getState().setSession({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: { id: 'u1', email: 'a@b.com', name: 'User' },
    })
    useWorkspaceStore.getState().setActiveWorkspaceId('w1')
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { status: 200 })
    )

    await apiRequest('/workspaces')

    const request = fetchMock.mock.calls[0][1] as RequestInit
    const headers = request.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer access-token')
    expect(headers.get('X-Workspace-Id')).toBe('w1')
  })

  it('refreshes token on 401 and retries request', async () => {
    useAuthStore.getState().setSession({
      accessToken: 'expired',
      refreshToken: 'refresh-token',
      user: { id: 'u1', email: 'a@b.com', name: 'User' },
    })
    let counter = 0
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url.endsWith('/auth/refresh')) {
        return new Response(
          JSON.stringify({
            accessToken: 'new-access',
            refreshToken: 'new-refresh',
            tokenType: 'Bearer',
            expiresIn: 300,
            user: { id: 'u1', email: 'a@b.com', name: 'User' },
          }),
          { status: 200 }
        )
      }
      counter += 1
      if (counter === 1) return new Response('{}', { status: 401 })
      return new Response(JSON.stringify({ ok: true }), { status: 200 })
    })

    const result = await apiRequest<{ ok: boolean }>('/secure')
    expect(result.ok).toBe(true)
    expect(useAuthStore.getState().accessToken).toBe('new-access')
  })

  it('parses error response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 403,
          errorCode: 'DENIED',
          message: 'Forbidden',
          path: '/x',
          requestId: 'req-1',
        }),
        { status: 403 }
      )
    )

    await expect(apiRequest('/x')).rejects.toBeInstanceOf(ApiError)
  })

  it('normalizes search page response with last field', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [],
          page: 1,
          size: 20,
          totalElements: 0,
          totalPages: 1,
          last: true,
        }),
        { status: 200 }
      )
    )

    const data = await apiRequest<{ hasNext: boolean; hasPrevious: boolean }>('/search/notes')
    expect(data.hasNext).toBe(false)
    expect(data.hasPrevious).toBe(true)
  })
})


import { useAuthStore } from '../../features/auth/auth-store'
import { useWorkspaceStore } from '../../features/workspaces/workspace-store'
import type { AuthResponse, ErrorResponse } from '../types/api'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export class ApiError extends Error {
  status: number
  errorCode: string
  requestId?: string
  fieldErrors?: ErrorResponse['fieldErrors']

  constructor(payload: ErrorResponse) {
    super(payload.message)
    this.status = payload.status
    this.errorCode = payload.errorCode
    this.requestId = payload.requestId
    this.fieldErrors = payload.fieldErrors
  }
}

type RequestOptions = RequestInit & { skipAuthRefresh?: boolean }
type ApiResponseWithMeta<T> = { data: T; response: Response }

const normalizePagination = <T extends { last?: boolean; hasNext?: boolean; hasPrevious?: boolean }>(
  payload: T
) => {
  if (typeof payload.hasNext === 'boolean' && typeof payload.hasPrevious === 'boolean') {
    return payload
  }
  if (typeof payload.last === 'boolean') {
    const page = (payload as unknown as { page?: number }).page ?? 0
    return {
      ...payload,
      hasNext: !payload.last,
      hasPrevious: page > 0,
    }
  }
  return payload
}

const parseError = async (response: Response): Promise<ApiError> => {
  let payload: ErrorResponse = {
    timestamp: new Date().toISOString(),
    status: response.status,
    errorCode: `HTTP_${response.status}`,
    message: response.statusText || 'Request failed',
    path: '',
  }
  try {
    payload = (await response.json()) as ErrorResponse
  } catch {
    // ignore body parse failure
  }
  return new ApiError(payload)
}

const refreshAccessToken = async (): Promise<boolean> => {
  const authStore = useAuthStore.getState()
  if (!authStore.refreshToken) return false
  try {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: authStore.refreshToken }),
    })
    if (!response.ok) return false
    const data = (await response.json()) as AuthResponse
    useAuthStore.getState().setSession({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      user: data.user,
    })
    return true
  } catch {
    return false
  }
}

export const apiRequest = async <T>(path: string, options: RequestOptions = {}): Promise<T> => {
  const payload = await apiRequestWithMeta<T>(path, options)
  return payload.data
}

export const apiRequestWithMeta = async <T>(
  path: string,
  options: RequestOptions = {}
): Promise<ApiResponseWithMeta<T>> => {
  const token = useAuthStore.getState().accessToken
  const workspaceId = useWorkspaceStore.getState().activeWorkspaceId
  const headers = new Headers(options.headers || {})
  headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (workspaceId) headers.set('X-Workspace-Id', workspaceId)

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  if (response.status === 401 && !options.skipAuthRefresh) {
    const refreshed = await refreshAccessToken()
    if (refreshed) {
      return apiRequestWithMeta<T>(path, { ...options, skipAuthRefresh: true })
    }
    useAuthStore.getState().clearSession()
    throw new ApiError({
      timestamp: new Date().toISOString(),
      status: 401,
      errorCode: 'AUTH_SESSION_EXPIRED',
      message: 'Session expired. Please login again.',
      path,
    })
  }
  if (!response.ok) {
    throw await parseError(response)
  }
  if (response.status === 204) {
    return { data: null as T, response }
  }
  const data = (await response.json()) as T
  if (data && typeof data === 'object' && 'items' in (data as Record<string, unknown>)) {
    return {
      data: normalizePagination(data as T & { last?: boolean }) as T,
      response,
    }
  }
  return { data, response }
}

export const readableErrorMessage = (error: unknown): string => {
  if (!(error instanceof ApiError)) return 'Unexpected error'
  switch (error.status) {
    case 400:
      return 'Validation error. Please check your input.'
    case 403:
      return 'Permission denied for this operation.'
    case 404:
      return 'Requested resource was not found.'
    case 429:
      return 'Rate limit reached. Please retry shortly.'
    case 503:
      return 'Service unavailable. Please retry later.'
    default:
      return error.message
  }
}


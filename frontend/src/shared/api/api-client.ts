import { useAuthStore } from '../../features/auth/auth-store'
import { useWorkspaceStore } from '../../features/workspaces/workspace-store'
import type { AuthResponse, ErrorResponse } from '../types/api'
import { getAuthTransport, isCookieMode } from '../config/auth-transport'

const runtimeApiBaseUrl = window.__NOTEBOOK_CONFIG__?.API_BASE_URL?.trim()
const API_BASE_URL = runtimeApiBaseUrl || import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

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
const CSRF_COOKIE_NAME = 'NP-XSRF-TOKEN'
const CSRF_HEADER_NAME = 'X-CSRF-Token'

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

const unsafeMethod = (method: string | undefined): boolean => {
  const normalized = (method || 'GET').toUpperCase()
  return normalized === 'POST' || normalized === 'PUT' || normalized === 'PATCH' || normalized === 'DELETE'
}

const readCookie = (name: string): string | null => {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]*)`))
  if (!match) return null
  try {
    return decodeURIComponent(match[1])
  } catch {
    return match[1]
  }
}

const refreshAccessToken = async (): Promise<boolean> => {
  const authStore = useAuthStore.getState()
  if (!isCookieMode() && !authStore.refreshToken) return false
  const transport = getAuthTransport()
  try {
    const headers = new Headers({ 'Content-Type': 'application/json' })
    const csrfToken = readCookie(CSRF_COOKIE_NAME)
    if (csrfToken) {
      headers.set(CSRF_HEADER_NAME, csrfToken)
    }
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers,
      credentials: isCookieMode() ? 'include' : 'same-origin',
      body: JSON.stringify(isCookieMode() ? {} : { refreshToken: authStore.refreshToken }),
    })
    if (!response.ok) return false
    const data = (await response.json()) as AuthResponse
    if (transport !== 'cookie') {
      useAuthStore.getState().setSession({
        accessToken: data.accessToken ?? null,
        refreshToken: data.refreshToken ?? null,
        user: data.user,
      })
    } else if (data.user) {
      useAuthStore.getState().setUser(data.user)
    }
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
  const transport = getAuthTransport()
  const workspaceId = useWorkspaceStore.getState().activeWorkspaceId
  const headers = new Headers(options.headers || {})
  headers.set('Content-Type', 'application/json')
  const shouldSendBearer = (transport === 'bearer' || transport === 'dual') && Boolean(token)
  if (shouldSendBearer) headers.set('Authorization', `Bearer ${token}`)
  if (workspaceId) headers.set('X-Workspace-Id', workspaceId)
  if (isCookieMode() && unsafeMethod(options.method)) {
    const csrfToken = readCookie(CSRF_COOKIE_NAME)
    if (csrfToken) {
      headers.set(CSRF_HEADER_NAME, csrfToken)
    }
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
    credentials: isCookieMode() ? 'include' : options.credentials,
  })
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


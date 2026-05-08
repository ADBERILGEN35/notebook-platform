import { apiRequest } from '../../../shared/api/api-client'
import type { PageResponse } from '../../../shared/types/api'
import { getAuditApiMode } from '../../../shared/config/admin-feature-flags'
import { useAuthStore } from '../../auth/auth-store'
import { getAuthTransport, isCookieMode } from '../../../shared/config/auth-transport'
import type { AuditEvent, AuditQueryFilters } from './types'
import { fetchAuditEventsMock } from './audit-mock-api'

export function auditFiltersToQueryString(filters: AuditQueryFilters): string {
  const p = new URLSearchParams()
  p.set('source', filters.source)
  if (filters.eventType) p.set('eventType', filters.eventType)
  if (filters.actorUserId) p.set('actorUserId', filters.actorUserId)
  if (filters.workspaceId) p.set('workspaceId', filters.workspaceId)
  if (filters.aggregateType) p.set('aggregateType', filters.aggregateType)
  if (filters.aggregateId) p.set('aggregateId', filters.aggregateId)
  if (filters.requestId) p.set('requestId', filters.requestId)
  if (filters.createdFrom) p.set('createdFrom', filters.createdFrom)
  if (filters.createdTo) p.set('createdTo', filters.createdTo)
  p.set('page', String(filters.page))
  p.set('size', String(filters.size))
  p.set('sort', filters.sort)
  return p.toString()
}

/**
 * Faz 43 gateway proxy contract. Do NOT call `/internal/audit-events` from the browser —
 * those routes require service JWT scope `internal:audit:read`.
 */
export async function fetchAuditEventsReal(filters: AuditQueryFilters): Promise<PageResponse<AuditEvent>> {
  const qs = auditFiltersToQueryString(filters)
  return await apiRequest<PageResponse<AuditEvent>>(`/admin/audit-events?${qs}`, { method: 'GET' })
}

export async function queryAuditEvents(filters: AuditQueryFilters): Promise<PageResponse<AuditEvent>> {
  if (getAuditApiMode() === 'mock') return fetchAuditEventsMock(filters)
  return fetchAuditEventsReal(filters)
}

export async function exportAuditEvents(
  filters: AuditQueryFilters,
  format: 'csv' | 'jsonl',
): Promise<{ blob: Blob; fileName: string; contentType: string }> {
  const qs = auditFiltersToQueryString(filters)
  const url = `/admin/audit-events/export?${qs}&format=${format}`
  const runtimeApiBaseUrl = window.__NOTEBOOK_CONFIG__?.API_BASE_URL?.trim()
  const apiBaseUrl = runtimeApiBaseUrl || import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
  const headers = new Headers()
  const token = useAuthStore.getState().accessToken
  if ((getAuthTransport() === 'bearer' || getAuthTransport() === 'dual') && token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${apiBaseUrl}${url}`, {
    method: 'GET',
    headers,
    credentials: isCookieMode() ? 'include' : 'same-origin',
  })
  if (!response.ok) {
    const err = await response.json().catch(() => null)
    const message = err?.message || 'Audit export failed'
    const error = new Error(message) as Error & { status?: number; errorCode?: string }
    error.status = err?.status || response.status
    error.errorCode = err?.errorCode || `HTTP_${response.status}`
    throw error
  }
  const disposition = response.headers.get('Content-Disposition') || ''
  const fileNameMatch = disposition.match(/filename="([^"]+)"/i)
  const fileName = fileNameMatch?.[1] || `audit-export.${format === 'csv' ? 'csv' : 'jsonl'}`
  return {
    blob: await response.blob(),
    fileName: fileName.replace(/[^\w.\-]/g, '_'),
    contentType: response.headers.get('Content-Type') || '',
  }
}

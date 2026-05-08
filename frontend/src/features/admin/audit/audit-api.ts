import { apiRequest } from '../../../shared/api/api-client'
import type { PageResponse } from '../../../shared/types/api'
import { getAuditApiMode } from '../../../shared/config/admin-feature-flags'
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

import { auditQueryFiltersSchema, defaultAuditFilters, auditSourceSchema } from './audit-schema'
import type { AuditQueryFilters } from './types'

/** Serialize filters for `/app/admin/audit` URL (no nested JSON). */
export function auditFiltersToUrlParams(f: AuditQueryFilters): URLSearchParams {
  const p = new URLSearchParams()
  p.set('source', f.source)
  if (f.eventType) p.set('eventType', f.eventType)
  if (f.actorUserId) p.set('actorUserId', f.actorUserId)
  if (f.workspaceId) p.set('workspaceId', f.workspaceId)
  if (f.aggregateType) p.set('aggregateType', f.aggregateType)
  if (f.aggregateId) p.set('aggregateId', f.aggregateId)
  if (f.requestId) p.set('requestId', f.requestId)
  if (f.createdFrom) p.set('createdFrom', f.createdFrom)
  if (f.createdTo) p.set('createdTo', f.createdTo)
  p.set('page', String(f.page))
  p.set('size', String(f.size))
  p.set('sort', f.sort)
  return p
}

export function auditFiltersFromUrlParams(search: URLSearchParams): {
  filters: AuditQueryFilters
  parseWarning: string | null
} {
  const raw: Record<string, string> = {}
  search.forEach((value, key) => {
    raw[key] = value
  })
  const parsed = auditQueryFiltersSchema.safeParse({
    source: raw.source,
    eventType: raw.eventType,
    actorUserId: raw.actorUserId,
    workspaceId: raw.workspaceId,
    aggregateType: raw.aggregateType,
    aggregateId: raw.aggregateId,
    requestId: raw.requestId,
    createdFrom: raw.createdFrom,
    createdTo: raw.createdTo,
    page: raw.page,
    size: raw.size,
    sort: raw.sort,
  })

  if (parsed.success) {
    return { filters: parsed.data, parseWarning: null }
  }

  const first = parsed.error.issues[0]
  const message = first?.message ?? 'Invalid query parameters'
  const src = auditSourceSchema.safeParse(raw.source)
  const source = src.success ? src.data : 'workspace'
  return { filters: defaultAuditFilters(source), parseWarning: message }
}

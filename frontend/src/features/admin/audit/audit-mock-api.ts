import type { PageResponse } from '../../../shared/types/api'
import type { AuditEvent, AuditQueryFilters } from './types'

const BASE_AUDIT_EVENTS: AuditEvent[] = [
  {
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    source: 'identity',
    eventType: 'USER_LOGIN_SUCCEEDED',
    actorUserId: '11111111-1111-1111-1111-111111111111',
    workspaceId: null,
    aggregateType: 'USER',
    aggregateId: '11111111-1111-1111-1111-111111111111',
    requestId: 'req-login-1',
    ipAddress: '203.0.113.10',
    userAgent: 'Mozilla/5.0 (e2e)',
    metadata: { channel: 'browser' },
    createdAt: '2026-05-01T10:00:00Z',
  },
  {
    id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    source: 'identity',
    eventType: 'REFRESH_TOKEN_ROTATED',
    actorUserId: '11111111-1111-1111-1111-111111111111',
    workspaceId: null,
    aggregateType: 'REFRESH_TOKEN',
    aggregateId: '22222222-2222-2222-2222-222222222222',
    requestId: 'req-refresh-1',
    ipAddress: '203.0.113.11',
    userAgent: 'curl/8',
    metadata: {
      replacedTokenId: '33333333-3333-3333-3333-333333333333',
      bearerToken: 'should-mask-in-ui',
    },
    createdAt: '2026-05-02T08:15:00Z',
  },
  {
    id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
    source: 'workspace',
    eventType: 'WORKSPACE_CREATED',
    actorUserId: '11111111-1111-1111-1111-111111111111',
    workspaceId: '44444444-4444-4444-4444-444444444444',
    aggregateType: 'WORKSPACE',
    aggregateId: '44444444-4444-4444-4444-444444444444',
    requestId: 'req-ws-1',
    ipAddress: '203.0.113.12',
    userAgent: 'Mozilla/5.0',
    metadata: { slug: 'demo', type: 'TEAM' },
    createdAt: '2026-05-03T12:30:00Z',
  },
  {
    id: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
    source: 'content',
    eventType: 'NOTE_UPDATED',
    actorUserId: '11111111-1111-1111-1111-111111111111',
    workspaceId: '44444444-4444-4444-4444-444444444444',
    aggregateType: 'NOTE',
    aggregateId: '55555555-5555-5555-5555-555555555555',
    requestId: 'req-note-1',
    ipAddress: '203.0.113.13',
    userAgent: 'Mozilla/5.0',
    metadata: {
      notebookId: '66666666-6666-6666-6666-666666666666',
      preview: 'Hello',
    },
    createdAt: '2026-05-04T09:45:00Z',
  },
  {
    id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    source: 'content',
    eventType: 'NOTE_CONFLICT_DETECTED',
    actorUserId: '99999999-9999-9999-9999-999999999999',
    workspaceId: '44444444-4444-4444-4444-444444444444',
    aggregateType: 'NOTE',
    aggregateId: '55555555-5555-5555-5555-555555555555',
    requestId: 'req-note-2',
    ipAddress: '198.51.100.2',
    userAgent: 'Mozilla/5.0',
    metadata: { secretKey: 'never-show', expectedRevision: 2, actualRevision: 3 },
    createdAt: '2026-05-05T16:00:00Z',
  },
]

const EXTRA_IDENTITY_MOCK_EVENTS: AuditEvent[] = Array.from({ length: 30 }, (_, index) => {
  const suffix = index.toString(16).padStart(12, '0')
  return {
    id: `f0000000-0000-4000-a000-${suffix}`,
    source: 'identity' as const,
    eventType: 'USER_LOGIN_SUCCEEDED',
    actorUserId: '11111111-1111-1111-1111-111111111111',
    workspaceId: null,
    aggregateType: 'USER',
    aggregateId: '11111111-1111-1111-1111-111111111111',
    requestId: `bulk-req-${index}`,
    ipAddress: '203.0.113.99',
    userAgent: 'mock-client',
    metadata: { index },
    createdAt: new Date(Date.UTC(2026, 0, 10 + index, 8, 0, 0)).toISOString(),
  }
})

/** Deterministic mock data for UI + E2E (includes bulk identity rows for pagination tests). */
export const MOCK_AUDIT_EVENTS: AuditEvent[] = [...BASE_AUDIT_EVENTS, ...EXTRA_IDENTITY_MOCK_EVENTS]

function matchesFilters(e: AuditEvent, f: AuditQueryFilters): boolean {
  if (e.source !== f.source) return false
  if (f.eventType && e.eventType !== f.eventType) return false
  if (f.actorUserId && e.actorUserId !== f.actorUserId) return false
  if (f.workspaceId && e.workspaceId !== f.workspaceId) return false
  if (f.aggregateType && e.aggregateType !== f.aggregateType) return false
  if (f.aggregateId && e.aggregateId !== f.aggregateId) return false
  if (f.requestId && e.requestId !== f.requestId) return false
  if (f.createdFrom) {
    const from = Date.parse(f.createdFrom)
    const t = Date.parse(e.createdAt)
    if (!Number.isNaN(from) && !Number.isNaN(t) && t < from) return false
  }
  if (f.createdTo) {
    const to = Date.parse(f.createdTo)
    const t = Date.parse(e.createdAt)
    if (!Number.isNaN(to) && !Number.isNaN(t) && t > to) return false
  }
  return true
}

function compare(a: AuditEvent, b: AuditEvent, field: string, dir: 'asc' | 'desc'): number {
  const sign = dir === 'asc' ? 1 : -1
  if (field === 'createdAt') {
    return sign * (Date.parse(a.createdAt) - Date.parse(b.createdAt))
  }
  if (field === 'eventType') return sign * a.eventType.localeCompare(b.eventType)
  if (field === 'aggregateType')
    return sign * String(a.aggregateType ?? '').localeCompare(String(b.aggregateType ?? ''))
  return 0
}

function parseSort(sort: string): { field: string; dir: 'asc' | 'desc' } {
  const [fieldRaw, dirRaw] = sort.split(',')
  const field =
    fieldRaw === 'eventType' || fieldRaw === 'aggregateType' || fieldRaw === 'createdAt'
      ? fieldRaw
      : 'createdAt'
  const dir = dirRaw === 'asc' ? 'asc' : 'desc'
  return { field, dir }
}

export async function fetchAuditEventsMock(filters: AuditQueryFilters): Promise<PageResponse<AuditEvent>> {
  await new Promise((r) => setTimeout(r, 150))
  const filtered = MOCK_AUDIT_EVENTS.filter((e) => matchesFilters(e, filters))
  const { field, dir } = parseSort(filters.sort)
  const sorted = [...filtered].sort((a, b) => compare(a, b, field, dir))

  const totalElements = sorted.length
  const start = filters.page * filters.size
  const items = sorted.slice(start, start + filters.size)
  const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / filters.size)

  return {
    items,
    page: filters.page,
    size: filters.size,
    totalElements,
    totalPages,
    hasNext: start + filters.size < totalElements,
    hasPrevious: filters.page > 0,
  }
}

export function findMockAuditEventById(id: string): AuditEvent | null {
  return MOCK_AUDIT_EVENTS.find((e) => e.id === id) ?? null
}

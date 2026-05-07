export type AuditSource = 'identity' | 'workspace' | 'content'

export type AuditEvent = {
  id: string
  source: AuditSource
  eventType: string
  actorUserId: string | null
  workspaceId: string | null
  aggregateType: string | null
  aggregateId: string | null
  requestId: string | null
  ipAddress: string | null
  userAgent: string | null
  metadata: unknown
  createdAt: string
}

export type AuditQueryFilters = {
  source: AuditSource
  eventType?: string | null
  actorUserId?: string | null
  workspaceId?: string | null
  aggregateType?: string | null
  aggregateId?: string | null
  requestId?: string | null
  createdFrom?: string | null
  createdTo?: string | null
  page: number
  size: number
  sort: string
}

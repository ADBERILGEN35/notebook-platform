import type { DeadLetterListItem } from '../notification-dead-letter-api'

export function maskRecipientHash(hash: string | null | undefined): string {
  const v = (hash ?? '').trim()
  if (!v) return '—'
  if (v.length <= 8) return `${v.slice(0, 2)}…`
  return `${v.slice(0, 4)}…${v.slice(-4)}`
}

export function maskActorId(id: string | null | undefined): string {
  const v = (id ?? '').trim()
  if (!v) return '—'
  if (v.length <= 10) return `${v.slice(0, 2)}…`
  return `${v.slice(0, 4)}…${v.slice(-4)}`
}

const SENSITIVE = /password|token|secret|bearer|authorization|email|@|body|payload|content/i

export function sanitizeDeadLetterForDisplay(row: DeadLetterListItem): Record<string, unknown> {
  return {
    id: row.id,
    source: row.source,
    eventType: row.eventType,
    status: row.status,
    recipientUserIdHash: maskRecipientHash(row.recipientUserIdHash),
    attemptCount: row.attemptCount,
    requeueCount: row.requeueCount,
    lastErrorCode: row.lastErrorCode,
    lastErrorSummary: row.lastErrorSummary,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    deadAt: row.deadAt,
  }
}

export function sanitizeLegalHoldReason(reason: string): string {
  const r = reason.trim()
  if (r.length <= 80) return r
  return `${r.slice(0, 40)}…${r.slice(-20)}`
}

export function containsSensitiveLeak(text: string): boolean {
  if (/eyJ[A-Za-z0-9_-]{10,}\./.test(text)) return true
  if (/Bearer\s+[A-Za-z0-9._-]{20,}/.test(text)) return true
  if (SENSITIVE.test(text) && /@/.test(text)) return true
  return false
}

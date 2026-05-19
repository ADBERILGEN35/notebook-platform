import type { ChangeRequestItem } from '../enterprise/change-requests-api'
import { isAdminRbacRoleChangeRequestOperation } from '../enterprise/change-requests-api'

export function rowSeverity(row: ChangeRequestItem): string {
  const fromRow = row.severity
  if (fromRow && String(fromRow).trim()) return String(fromRow)
  const im = row.impactSummary?.severity
  return typeof im === 'string' ? im : ''
}

export function isHighSeverity(row: ChangeRequestItem): boolean {
  return rowSeverity(row).toUpperCase() === 'HIGH'
}

export function isRbacRoleRequest(row: ChangeRequestItem): boolean {
  return isAdminRbacRoleChangeRequestOperation(row.operationType)
}

export function isPlatformAdminRoleRequest(row: ChangeRequestItem): boolean {
  if (!isRbacRoleRequest(row)) return false
  const payload = row.validationResult as Record<string, unknown> | null
  const role =
    (payload?.requestedRole as string | undefined) ??
    (row.impactSummary?.role as string | undefined) ??
    row.requestedValue
  return String(role).toUpperCase().includes('PLATFORM_ADMIN')
}

export function maskUserId(userId: string | null | undefined): string {
  const v = (userId ?? '').trim()
  if (!v) return '—'
  if (v.length <= 10) return `${v.slice(0, 2)}…${v.slice(-2)}`
  return `${v.slice(0, 4)}…${v.slice(-4)}`
}

const SENSITIVE_KEY = /password|token|secret|authorization|bearer|private|apikey|client_secret|webhook/i

/** Sanitize structured payloads for display (keys masked, no raw IdP claims). */
export function sanitizeStructuredPreview(raw: Record<string, unknown> | null | undefined): Record<string, unknown> {
  if (!raw) return {}
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(raw)) {
    if (/^idp|^saml|^oidc|claim|assertion|scim/i.test(k)) {
      out[k] = '***redacted***'
    } else if (SENSITIVE_KEY.test(k)) {
      out[k] = '***masked***'
    } else if (v && typeof v === 'object' && !Array.isArray(v)) {
      out[k] = sanitizeStructuredPreview(v as Record<string, unknown>)
    } else {
      out[k] = v
    }
  }
  return out
}

export function extractApiErrorFields(e: unknown): { errorCode: string; message: string } | null {
  if (typeof e === 'object' && e !== null) {
    const rec = e as Record<string, unknown>
    const code = rec.errorCode
    if (typeof code === 'string') {
      const msg = typeof rec.message === 'string' ? rec.message : 'Request failed'
      return { errorCode: code, message: msg }
    }
  }
  return null
}

export function gitOpsAvailableForRow(
  row: ChangeRequestItem,
  flags: { gitOpsUi: boolean; gitOpsRbacUi: boolean },
): boolean {
  if (row.status !== 'APPROVED') return false
  if (!flags.gitOpsUi) return false
  if (isRbacRoleRequest(row) && !flags.gitOpsRbacUi) return false
  return true
}

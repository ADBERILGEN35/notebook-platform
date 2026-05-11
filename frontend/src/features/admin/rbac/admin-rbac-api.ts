import { apiRequest } from '../../../shared/api/api-client'

export type AdminRbacRoleSource = {
  type: string
  sourceName: string
  roles: string[]
  reasonRef?: string | null
}

export type AdminRbacOverridesStatus = {
  enabled: boolean
  reloadEnabled: boolean
  lastKnownGoodEnabled: boolean
  failClosed: boolean
  fileConfigured: boolean
  loaded: boolean
  fileBasename: string
  loadedAt: string | null
  checksum: string
  manifestVersion: string
  lastReloadAttemptAt: string | null
  lastReloadResult: string
  assignmentCount: number
  validAssignmentCount: number
  ignoredAssignmentCount: number
  warningCount: number
  errorCount: number
  warnings: string[]
}

export type AdminRbacOverridesReloadResponse = {
  reloaded: boolean
  result: string
  checksum: string
  validAssignmentCount: number
  ignoredAssignmentCount: number
  warnings: string[]
}

/** Short display for sha256:… checksums (never raw YAML). */
export function formatOverrideChecksumShort(checksum: string | null | undefined): string {
  const c = (checksum ?? '').trim()
  if (!c) return '—'
  const lower = c.toLowerCase()
  const hex = lower.startsWith('sha256:') ? lower.slice('sha256:'.length) : lower
  if (hex.length <= 20) return lower.startsWith('sha256:') ? `sha256:${hex}` : c
  return `sha256:${hex.slice(0, 4)}…${hex.slice(-4)}`
}

export type AdminRbacUserRow = {
  userId: string
  email: string
  status: string
  platformRoles: string[]
  platformPermissions: string[]
  sources: AdminRbacRoleSource[]
  lastLoginAt: string | null
  mfaVerifiedRecently: boolean
  warnings: string[]
}

export type AdminRbacUserListResponse = {
  items: AdminRbacUserRow[]
  page: number
  size: number
  totalElements: number
}

export type AdminRbacUserDetailResponse = {
  user: AdminRbacUserRow
  pendingChangeRequestCount: number
}

export async function listAdminRbacUsers(params: {
  q?: string
  role?: string
  permission?: string
  page?: number
  size?: number
}): Promise<AdminRbacUserListResponse> {
  const sp = new URLSearchParams()
  if (params.q) sp.set('q', params.q)
  if (params.role) sp.set('role', params.role)
  if (params.permission) sp.set('permission', params.permission)
  sp.set('page', String(params.page ?? 0))
  sp.set('size', String(params.size ?? 25))
  const q = sp.toString()
  return apiRequest<AdminRbacUserListResponse>(`/admin/rbac/users?${q}`, { method: 'GET' })
}

export async function getAdminRbacUser(userId: string): Promise<AdminRbacUserDetailResponse> {
  return apiRequest<AdminRbacUserDetailResponse>(`/admin/rbac/users/${encodeURIComponent(userId)}`, {
    method: 'GET',
  })
}

export async function getAdminRbacOverridesStatus(): Promise<AdminRbacOverridesStatus> {
  return apiRequest<AdminRbacOverridesStatus>('/admin/rbac/overrides/status', { method: 'GET' })
}

export async function postAdminRbacOverridesReload(body: {
  reason: string
}): Promise<AdminRbacOverridesReloadResponse> {
  return apiRequest<AdminRbacOverridesReloadResponse>('/admin/rbac/overrides/reload', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

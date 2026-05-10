import { apiRequest } from '../../../shared/api/api-client'

export type AdminRbacRoleSource = {
  type: string
  sourceName: string
  roles: string[]
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

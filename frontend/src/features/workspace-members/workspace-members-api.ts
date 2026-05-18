import { apiRequest } from '../../shared/api/api-client'
import type { PageResponse } from '../../shared/types/api'
import type { WorkspaceInvitation, WorkspaceMember, WorkspaceRole } from './workspace-members-types'

export const listWorkspaceMembers = (workspaceId: string, page = 0, size = 50) =>
  apiRequest<PageResponse<WorkspaceMember>>(`/workspaces/${workspaceId}/members?page=${page}&size=${size}`)

export const listWorkspaceInvitations = (workspaceId: string, page = 0, size = 20) =>
  apiRequest<PageResponse<WorkspaceInvitation>>(`/workspaces/${workspaceId}/invitations?page=${page}&size=${size}`)

export const createWorkspaceInvitation = (
  workspaceId: string,
  payload: { email: string; role: WorkspaceRole },
) =>
  apiRequest<WorkspaceInvitation>(`/workspaces/${workspaceId}/invitations`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })

export const updateWorkspaceMemberRole = (workspaceId: string, userId: string, role: WorkspaceRole) =>
  apiRequest<WorkspaceMember>(`/workspaces/${workspaceId}/members/${userId}/role`, {
    method: 'PATCH',
    body: JSON.stringify({ role }),
  })

export const removeWorkspaceMember = (workspaceId: string, userId: string) =>
  apiRequest<void>(`/workspaces/${workspaceId}/members/${userId}`, { method: 'DELETE' })

export const revokeWorkspaceInvitation = (invitationId: string) =>
  apiRequest<WorkspaceInvitation>(`/invitations/${invitationId}/revoke`, {
    method: 'POST',
    body: JSON.stringify({}),
  })

import { apiRequest } from '../../shared/api/api-client'
import type { PageResponse, Workspace } from '../../shared/types/api'

export const listWorkspaces = (page = 0, size = 20) =>
  apiRequest<PageResponse<Workspace>>(`/workspaces?page=${page}&size=${size}&sort=updatedAt,desc`)

export const createWorkspace = (payload: {
  name: string
  slug?: string
  type: 'PERSONAL' | 'TEAM'
}) =>
  apiRequest<Workspace>('/workspaces', {
    method: 'POST',
    body: JSON.stringify(payload),
  })

export const getWorkspace = (workspaceId: string) =>
  apiRequest<Workspace>(`/workspaces/${workspaceId}`)

export const updateWorkspace = (
  workspaceId: string,
  payload: { name: string; slug: string },
) =>
  apiRequest<Workspace>(`/workspaces/${workspaceId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })


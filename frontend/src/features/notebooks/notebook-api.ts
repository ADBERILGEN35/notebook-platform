import { apiRequest } from '../../shared/api/api-client'
import type { Notebook, PageResponse } from '../../shared/types/api'

export const listNotebooks = (workspaceId: string, page = 0, size = 20) =>
  apiRequest<PageResponse<Notebook>>(
    `/workspaces/${workspaceId}/notebooks?page=${page}&size=${size}&sort=updatedAt,desc`
  )

export const createNotebook = (workspaceId: string, payload: { name: string; icon?: string }) =>
  apiRequest<Notebook>(`/workspaces/${workspaceId}/notebooks`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })

export const getNotebook = (notebookId: string) => apiRequest<Notebook>(`/notebooks/${notebookId}`)


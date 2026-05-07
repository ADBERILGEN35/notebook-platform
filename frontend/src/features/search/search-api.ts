import { apiRequest } from '../../shared/api/api-client'
import type { PageResponse, SearchNoteResult } from '../../shared/types/api'

export const searchNotes = (workspaceId: string, query: string, page = 0, size = 20) =>
  apiRequest<PageResponse<SearchNoteResult>>(
    `/search/notes?workspaceId=${workspaceId}&q=${encodeURIComponent(query)}&page=${page}&size=${size}`
  )


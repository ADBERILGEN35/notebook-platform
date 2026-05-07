import { apiRequest } from '../../shared/api/api-client'
import type { NoteComment, PageResponse } from '../../shared/types/api'

export const listComments = (noteId: string, page = 0, size = 20) =>
  apiRequest<PageResponse<NoteComment>>(`/notes/${noteId}/comments?page=${page}&size=${size}&sort=createdAt,desc`)

export const createComment = (noteId: string, payload: { content: string; blockId?: string }) =>
  apiRequest<NoteComment>(`/notes/${noteId}/comments`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })

export const resolveComment = (commentId: string) =>
  apiRequest<NoteComment>(`/comments/${commentId}/resolve`, {
    method: 'POST',
    body: JSON.stringify({}),
  })

export const reopenComment = (commentId: string) =>
  apiRequest<NoteComment>(`/comments/${commentId}/reopen`, {
    method: 'POST',
    body: JSON.stringify({}),
  })


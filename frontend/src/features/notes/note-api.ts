import { apiRequest, apiRequestWithMeta } from '../../shared/api/api-client'
import type { Note, NoteBlock, PageResponse } from '../../shared/types/api'

export type NoteWithEtag = {
  note: Note
  etag: string | null
}

export const listNotes = (notebookId: string, page = 0, size = 20) =>
  apiRequest<PageResponse<Note>>(`/notebooks/${notebookId}/notes?page=${page}&size=${size}&sort=updatedAt,desc`)

export const getNote = async (noteId: string): Promise<NoteWithEtag> => {
  const { data, response } = await apiRequestWithMeta<Note>(`/notes/${noteId}`)
  return { note: data, etag: response.headers.get('ETag') }
}

export const createNote = (notebookId: string, payload: { title: string; contentBlocks: NoteBlock[] }) =>
  apiRequest<Note>(`/notebooks/${notebookId}/notes`, {
    method: 'POST',
    body: JSON.stringify({ ...payload, contentSchemaVersion: 1 }),
  })

export const updateNote = async (
  noteId: string,
  payload: { title: string; contentBlocks: NoteBlock[] },
  ifMatch?: string | null
): Promise<NoteWithEtag> => {
  const headers: Record<string, string> = {}
  if (ifMatch) {
    headers['If-Match'] = ifMatch
  }
  const { data, response } = await apiRequestWithMeta<Note>(`/notes/${noteId}`, {
    method: 'PATCH',
    headers,
    body: JSON.stringify(payload),
  })
  return { note: data, etag: response.headers.get('ETag') }
}


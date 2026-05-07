import { apiRequest, apiRequestWithMeta } from '../../shared/api/api-client'
import type { NoteWithEtag } from '../notes/note-api'
import type { Note, NoteVersion, PageResponse } from '../../shared/types/api'

export const listVersions = (noteId: string, page = 0, size = 20) =>
  apiRequest<PageResponse<NoteVersion>>(`/notes/${noteId}/versions?page=${page}&size=${size}&sort=versionNumber,desc`)

export const restoreVersion = async (
  noteId: string,
  versionNumber: number,
  ifMatch?: string | null
): Promise<NoteWithEtag> => {
  const headers: Record<string, string> = {}
  if (ifMatch) {
    headers['If-Match'] = ifMatch
  }
  const { data, response } = await apiRequestWithMeta<Note>(`/notes/${noteId}/restore/${versionNumber}`, {
    method: 'POST',
    headers,
    body: JSON.stringify({}),
  })
  return { note: data, etag: response.headers.get('ETag') }
}


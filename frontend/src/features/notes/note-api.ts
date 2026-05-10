import { apiRequest, apiRequestWithMeta } from '../../shared/api/api-client'
import type { Note, NoteBlock, PageResponse } from '../../shared/types/api'
import type { NoteSaveSnapshot } from './utils/note-save-snapshot'

export type NoteWithEtag = {
  note: Note
  etag: string | null
}

export type BackendMergeAnalyzeResponse = {
  noteId: string
  baseEtag: string | null
  remoteEtag: string
  mergeVersion: number
  supportedMergeVersions: number[]
  canAutoMerge: boolean
  hasConflicts: boolean
  summary: {
    localChanges: string[]
    remoteChanges: string[]
    conflicts: string[]
  }
  suggested: {
    title: string
    contentBlocks: NoteBlock[]
  } | null
  conflicts: Array<{
    type: string
    blockId?: string | null
    message: string
  }>
}

export type BackendMergeApplyResponse = {
  noteId: string
  merged: boolean
  mergeVersion: number
  etag: string
  version: number
  title: string
  contentBlocks: NoteBlock[]
  appliedConflicts: Array<{
    type: string
    blockId?: string | null
    message: string
  }>
  summary: {
    localChanges: string[]
    remoteChanges: string[]
    conflicts: string[]
  }
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

export const analyzeMerge = (noteId: string, payload: {
  base: NoteSaveSnapshot & { etag: string | null }
  local: Pick<NoteSaveSnapshot, 'title' | 'contentBlocks'>
  clientMergeVersion: number
}) =>
  apiRequest<BackendMergeAnalyzeResponse>(`/notes/${noteId}/merge/analyze`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })

export const applyMerge = (noteId: string, payload: {
  base: NoteSaveSnapshot & { etag: string | null }
  local: Pick<NoteSaveSnapshot, 'title' | 'contentBlocks'>
  expectedRemoteEtag: string
  mergeVersion: number
  idempotencyKey?: string
}) =>
  apiRequest<BackendMergeApplyResponse>(`/notes/${noteId}/merge/apply`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })


export type FieldError = {
  field: string
  message: string
}

export type ErrorResponse = {
  timestamp: string
  status: number
  errorCode: string
  message: string
  path: string
  requestId?: string
  fieldErrors?: FieldError[]
}

export type PageResponse<T> = {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export type AuthUser = {
  id: string
  email: string
  name: string
  avatarUrl?: string | null
  status?: string
}

export type AuthResponse = {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}

export type Workspace = {
  id: string
  slug: string
  name: string
  type: 'PERSONAL' | 'TEAM'
  ownerId: string
  createdAt: string
  updatedAt: string
  archivedAt?: string | null
}

export type Notebook = {
  id: string
  workspaceId: string
  name: string
  icon?: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
  archivedAt?: string | null
}

export type NoteBlock = {
  id: string
  type: string
  content: unknown[]
  props: Record<string, unknown>
  children: unknown[]
}

export type Note = {
  id: string
  workspaceId: string
  notebookId: string
  parentNoteId?: string | null
  title: string
  contentBlocks: NoteBlock[]
  contentSchemaVersion: number
  createdAt: string
  updatedAt: string
  archivedAt?: string | null
}

export type NoteVersion = {
  id: string
  noteId: string
  versionNumber: number
  title: string
  createdAt: string
}

export type NoteComment = {
  id: string
  noteId: string
  userId: string
  content: string
  blockId?: string | null
  resolvedAt?: string | null
  createdAt: string
  updatedAt: string
}

export type SearchNoteResult = {
  noteId: string
  workspaceId: string
  notebookId?: string | null
  title: string
  snippet: string
  rank: number
  noteUpdatedAt?: string | null
}


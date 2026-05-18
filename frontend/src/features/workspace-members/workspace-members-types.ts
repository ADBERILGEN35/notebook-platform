export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER'

export type WorkspaceMember = {
  workspaceId: string
  userId: string
  role: WorkspaceRole
  joinedAt: string
  createdAt: string
  updatedAt: string
}

export type WorkspaceInvitation = {
  id: string
  workspaceId: string
  email: string
  role: WorkspaceRole
  expiresAt: string
  acceptedAt?: string | null
  revokedAt?: string | null
  createdBy: string
  createdAt: string
}

import type { Notebook, Workspace } from '../../shared/types/api'

export type AppShellNavProps = {
  workspaces: Workspace[]
  notebooks: Notebook[]
  activeWorkspaceId: string | null
  showAdminNav?: boolean
  onWorkspaceSelect: (workspaceId: string) => void
  onNavigate?: () => void
  className?: string
}

export type AppShellTopProps = {
  search: string
  onSearchChange: (value: string) => void
  onCreateNote: () => void
  onSidebarToggle?: () => void
  isOnline?: boolean
  workspaces: Workspace[]
  activeWorkspaceId: string | null
  onWorkspaceSelect: (workspaceId: string) => void
  onOpenSearch?: () => void
}

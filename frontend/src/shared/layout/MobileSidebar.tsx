import type { Notebook, Workspace } from '../types/api'
import { Sidebar } from './Sidebar'
import { ResponsiveDrawer } from '../components/ResponsiveDrawer'

type Props = {
  open: boolean
  onClose: () => void
  workspaces: Workspace[]
  notebooks: Notebook[]
  activeWorkspaceId: string | null
  onWorkspaceSelect: (workspaceId: string) => void
  showAdminNav?: boolean
}

export function MobileSidebar(props: Props) {
  return (
    <ResponsiveDrawer
      open={props.open}
      onClose={props.onClose}
      title="Navigation"
      side="left"
      testId="mobile-sidebar"
    >
      <Sidebar
        workspaces={props.workspaces}
        notebooks={props.notebooks}
        activeWorkspaceId={props.activeWorkspaceId}
        onWorkspaceSelect={props.onWorkspaceSelect}
        showAdminNav={props.showAdminNav}
        onNavigate={props.onClose}
        className="w-full border-r-0 p-0"
      />
    </ResponsiveDrawer>
  )
}

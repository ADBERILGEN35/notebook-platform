import { create } from 'zustand'

const ACTIVE_WORKSPACE_KEY = 'np_active_workspace'

type WorkspaceState = {
  activeWorkspaceId: string | null
  setActiveWorkspaceId: (id: string | null) => void
}

export const useWorkspaceStore = create<WorkspaceState>((set) => ({
  activeWorkspaceId: localStorage.getItem(ACTIVE_WORKSPACE_KEY),
  setActiveWorkspaceId: (id) => {
    if (id) {
      localStorage.setItem(ACTIVE_WORKSPACE_KEY, id)
    } else {
      localStorage.removeItem(ACTIVE_WORKSPACE_KEY)
    }
    set({ activeWorkspaceId: id })
  },
}))


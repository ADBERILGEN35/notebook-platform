import { Link } from 'react-router-dom'
import type { Notebook, Workspace } from '../types/api'

type Props = {
  workspaces: Workspace[]
  notebooks: Notebook[]
  activeWorkspaceId: string | null
  onWorkspaceSelect: (workspaceId: string) => void
}

export function Sidebar({ workspaces, notebooks, activeWorkspaceId, onWorkspaceSelect }: Props) {
  return (
    <aside className="w-72 border-r border-slate-200 bg-white p-3">
      <div className="mb-4">
        <p className="mb-2 text-xs font-semibold uppercase text-slate-500">Workspaces</p>
        <div className="space-y-1">
          {workspaces.map((workspace) => (
            <button
              key={workspace.id}
              onClick={() => onWorkspaceSelect(workspace.id)}
              className={`block w-full rounded px-2 py-1 text-left text-sm ${activeWorkspaceId === workspace.id ? 'bg-primary-100 text-primary-700' : 'hover:bg-slate-100'}`}
            >
              {workspace.name}
            </button>
          ))}
        </div>
      </div>
      <div>
        <p className="mb-2 text-xs font-semibold uppercase text-slate-500">Notebooks</p>
        <div className="space-y-1">
          {notebooks.map((notebook) => (
            <Link
              key={notebook.id}
              to={`/app/notebooks/${notebook.id}`}
              className="block rounded px-2 py-1 text-sm hover:bg-slate-100"
            >
              {notebook.name}
            </Link>
          ))}
        </div>
      </div>
      <div className="mt-6 space-y-1 text-sm text-slate-600">
        <Link to="/app/search" className="block rounded px-2 py-1 hover:bg-slate-100">
          Search
        </Link>
        <Link to="/app/settings" className="block rounded px-2 py-1 hover:bg-slate-100">
          Settings
        </Link>
      </div>
    </aside>
  )
}


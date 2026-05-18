import type { Workspace } from '../../../shared/types/api'

type WorkspaceSwitcherProps = {
  workspaces: Workspace[]
  activeWorkspaceId: string | null
  onSelect: (workspaceId: string) => void
  className?: string
}

export function WorkspaceSwitcher({ workspaces, activeWorkspaceId, onSelect, className = '' }: WorkspaceSwitcherProps) {
  const active = workspaces.find((w) => w.id === activeWorkspaceId)
  return (
    <label className={`flex min-w-0 flex-col gap-1 ${className}`}>
      <span className="text-label-md text-on-surface-variant">Workspace</span>
      <select
        className="max-w-[14rem] rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-body-md text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary-fixed-dim"
        value={activeWorkspaceId ?? ''}
        onChange={(event) => onSelect(event.target.value)}
        aria-label="Switch workspace"
      >
        {!activeWorkspaceId ? <option value="">Select workspace</option> : null}
        {workspaces.map((workspace) => (
          <option key={workspace.id} value={workspace.id}>
            {workspace.name}
          </option>
        ))}
      </select>
      {active ? <span className="sr-only">Active: {active.name}</span> : null}
    </label>
  )
}

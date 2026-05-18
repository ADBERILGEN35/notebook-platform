import { Link, useLocation } from 'react-router-dom'
import type { AppShellNavProps } from '../types'

const navLinkClass = (active: boolean) =>
  `block rounded-lg px-3 py-2 text-body-md transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
    active ? 'bg-primary-fixed font-medium text-primary' : 'text-on-surface-variant hover:bg-surface-container-low'
  }`

export function SideNav({
  workspaces,
  notebooks,
  activeWorkspaceId,
  onWorkspaceSelect,
  showAdminNav,
  className = '',
  onNavigate,
}: AppShellNavProps) {
  const location = useLocation()

  return (
    <aside
      className={`flex h-full w-72 shrink-0 flex-col border-r border-outline-variant bg-surface-container-lowest p-4 ${className}`}
      aria-label="Main navigation"
    >
      <Link
        to="/app/workspaces"
        className="mb-4 font-display text-headline-sm text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        onClick={onNavigate}
      >
        Notebook Platform
      </Link>

      <nav className="flex-1 space-y-6 overflow-y-auto" aria-label="Workspace navigation">
        <section aria-labelledby="nav-workspaces-heading">
          <h2 id="nav-workspaces-heading" className="mb-2 text-label-md font-semibold uppercase text-on-surface-variant">
            Workspaces
          </h2>
          <ul className="space-y-1">
            {workspaces.map((workspace) => (
              <li key={workspace.id}>
                <button
                  type="button"
                  onClick={() => {
                    onWorkspaceSelect(workspace.id)
                    onNavigate?.()
                  }}
                  className={`w-full text-left ${navLinkClass(activeWorkspaceId === workspace.id)}`}
                  aria-current={activeWorkspaceId === workspace.id ? 'true' : undefined}
                >
                  {workspace.name}
                </button>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="nav-notebooks-heading">
          <h2 id="nav-notebooks-heading" className="mb-2 text-label-md font-semibold uppercase text-on-surface-variant">
            Notebooks
          </h2>
          <ul className="space-y-1">
            {notebooks.length === 0 ? (
              <li className="px-3 text-label-md text-on-surface-variant">No notebooks yet</li>
            ) : (
              notebooks.map((notebook) => (
                <li key={notebook.id}>
                  <Link
                    to={`/app/notebooks/${notebook.id}`}
                    className={navLinkClass(location.pathname.includes(notebook.id))}
                    onClick={onNavigate}
                  >
                    {notebook.name}
                  </Link>
                </li>
              ))
            )}
          </ul>
        </section>
      </nav>

      <nav className="mt-4 space-y-1 border-t border-outline-variant pt-4" aria-label="App links">
        <Link to="/app/workspaces" className={navLinkClass(location.pathname.startsWith('/app/workspaces'))} onClick={onNavigate}>
          Hub
        </Link>
        <Link
          to="/app/search"
          className={navLinkClass(location.pathname === '/app/search' || location.pathname.startsWith('/app/search/'))}
          onClick={onNavigate}
        >
          Search
        </Link>
        <Link to="/app/search/discover" className={navLinkClass(location.pathname === '/app/search/discover')} onClick={onNavigate}>
          Discover
        </Link>
        <Link to="/app/notifications" className={navLinkClass(location.pathname === '/app/notifications')} onClick={onNavigate}>
          Notifications
        </Link>
        {showAdminNav ? (
          <Link to="/app/admin" className={navLinkClass(location.pathname.startsWith('/app/admin'))} onClick={onNavigate}>
            Admin
          </Link>
        ) : null}
        <Link to="/app/settings" className={navLinkClass(location.pathname.startsWith('/app/settings'))} onClick={onNavigate}>
          Settings
        </Link>
      </nav>
    </aside>
  )
}

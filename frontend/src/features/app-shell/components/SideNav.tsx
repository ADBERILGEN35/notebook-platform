import { Link, useLocation } from 'react-router-dom'
import { useEffect, useState, type ReactNode } from 'react'
import type { AppShellNavProps } from '../types'

type NavItem = {
  to: string
  label: string
  icon: ReactNode
  match: (pathname: string) => boolean
}

const ICONS = {
  workspaces: (
    <svg viewBox="0 0 24 24" aria-hidden className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" />
    </svg>
  ),
  search: (
    <svg viewBox="0 0 24 24" aria-hidden className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.75">
      <circle cx="11" cy="11" r="6" />
      <path d="m20 20-3.5-3.5" strokeLinecap="round" />
    </svg>
  ),
  notifications: (
    <svg viewBox="0 0 24 24" aria-hidden className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M6 9a6 6 0 0 1 12 0v4l2 3H4l2-3V9Z" />
      <path d="M10 19a2 2 0 0 0 4 0" strokeLinecap="round" />
    </svg>
  ),
  settings: (
    <svg viewBox="0 0 24 24" aria-hidden className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.75">
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3v2m0 14v2M3 12h2m14 0h2M5.6 5.6l1.4 1.4m9.9 9.9 1.5 1.5m0-12.8L17 6.9M6.9 17l-1.3 1.4" />
    </svg>
  ),
  admin: (
    <svg viewBox="0 0 24 24" aria-hidden className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="m12 3 8 3v6c0 4.5-3 8.5-8 9-5-.5-8-4.5-8-9V6l8-3Z" />
      <path d="m9 12 2 2 4-4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  support: (
    <svg viewBox="0 0 24 24" aria-hidden className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.75">
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 9.5a2.5 2.5 0 1 1 4 2c-1 .5-1.5 1.2-1.5 2M12 17h.01" strokeLinecap="round" />
    </svg>
  ),
  signOut: (
    <svg viewBox="0 0 24 24" aria-hidden className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M15 5h3a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-3" />
      <path d="M10 12h10m-3-3 3 3-3 3" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  plus: (
    <svg viewBox="0 0 24 24" aria-hidden className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M12 5v14M5 12h14" strokeLinecap="round" />
    </svg>
  ),
  chevron: (
    <svg viewBox="0 0 24 24" aria-hidden className="h-4 w-4 transition-transform group-open:rotate-90" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="m9 6 6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
}

const navLinkClass = (active: boolean) =>
  `group flex items-center gap-3 rounded-lg px-3 py-2 text-body-md transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
    active
      ? 'bg-primary-fixed font-semibold text-primary'
      : 'text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface'
  }`

/**
 * Faz 151C SideNav — enterprise navigation rail.
 *
 * Reference: docs/design/workspace_dashboard_empty (240px rail, NP brand mark,
 * primary "New Notebook" CTA, compact icon+label navigation, sticky support /
 * sign-out at the bottom).
 *
 * Workspaces remain switchable via a `<details>` panel so existing routes and
 * tests (`button name=workspace`) keep working without breaking the design.
 */
export function SideNav({
  workspaces,
  activeWorkspaceId,
  onWorkspaceSelect,
  onCreateNotebook,
  onSignOut,
  signOutPending,
  showAdminNav,
  className = '',
  onNavigate,
}: AppShellNavProps) {
  const location = useLocation()
  const onWorkspacesRoute =
    location.pathname === '/app' || location.pathname.startsWith('/app/workspaces')
  const [workspacesOpen, setWorkspacesOpen] = useState(true)
  useEffect(() => {
    if (onWorkspacesRoute) setWorkspacesOpen(true)
  }, [onWorkspacesRoute])

  const navItems: NavItem[] = [
    {
      to: '/app/workspaces',
      label: 'Workspaces',
      icon: ICONS.workspaces,
      match: (p) => p === '/app' || p.startsWith('/app/workspaces'),
    },
    {
      to: '/app/search',
      label: 'Search',
      icon: ICONS.search,
      match: (p) => p === '/app/search' || p.startsWith('/app/search/'),
    },
    {
      to: '/app/notifications',
      label: 'Notifications',
      icon: ICONS.notifications,
      match: (p) => p === '/app/notifications',
    },
    {
      to: '/app/settings',
      label: 'Settings',
      icon: ICONS.settings,
      match: (p) => p.startsWith('/app/settings'),
    },
  ]

  return (
    <aside
      className={`flex h-full w-60 shrink-0 flex-col border-r border-outline-variant bg-surface px-4 py-5 ${className}`}
      aria-label="Main navigation"
    >
      <Link
        to="/app/workspaces"
        onClick={onNavigate}
        className="mb-6 flex items-center gap-3 rounded-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        aria-label="Notebook Platform home"
      >
        <span
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary-container text-headline-sm font-bold text-on-primary"
          aria-hidden
        >
          NP
        </span>
        <span className="flex flex-col">
          <span className="font-display text-headline-sm font-bold text-primary">Notebook Platform</span>
          <span className="text-label-md text-on-surface-variant">Enterprise Workspace</span>
        </span>
      </Link>

      <button
        type="button"
        onClick={() => {
          onCreateNotebook?.()
          onNavigate?.()
        }}
        disabled={!onCreateNotebook || !activeWorkspaceId}
        className="mb-6 flex w-full items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md font-semibold text-on-primary shadow-sm transition-colors hover:bg-primary-container focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60"
        aria-label={activeWorkspaceId ? 'Create new notebook' : 'Select a workspace to create a notebook'}
        data-testid="sidenav-new-notebook"
      >
        {ICONS.plus}
        New Notebook
      </button>

      <nav className="flex-1 space-y-1 overflow-y-auto" aria-label="Workspace navigation">
        {navItems.map((item) => {
          const active = item.match(location.pathname)
          if (item.label !== 'Workspaces' || workspaces.length === 0) {
            return (
              <Link key={item.to} to={item.to} className={navLinkClass(active)} onClick={onNavigate}>
                <span className={active ? 'text-primary' : 'text-on-surface-variant'}>{item.icon}</span>
                <span>{item.label}</span>
              </Link>
            )
          }
          return (
            <details
              key={item.to}
              className="group"
              open={workspacesOpen}
              onToggle={(event) => setWorkspacesOpen(event.currentTarget.open)}
            >
              <summary
                className={`${navLinkClass(active)} list-none cursor-pointer [&::-webkit-details-marker]:hidden`}
              >
                <span className={active ? 'text-primary' : 'text-on-surface-variant'}>{item.icon}</span>
                <span className="flex-1">{item.label}</span>
                <span className={active ? 'text-primary' : 'text-on-surface-variant'}>{ICONS.chevron}</span>
              </summary>
              <ul className="mt-1 ml-9 space-y-1 border-l border-outline-variant pl-3">
                <li>
                  <Link
                    to="/app/workspaces"
                    onClick={onNavigate}
                    className="block rounded-md px-2 py-1 text-label-md text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                  >
                    All workspaces
                  </Link>
                </li>
                {workspaces.map((workspace) => (
                  <li key={workspace.id}>
                    <button
                      type="button"
                      onClick={() => {
                        onWorkspaceSelect(workspace.id)
                        onNavigate?.()
                      }}
                      className={`w-full truncate rounded-md px-2 py-1 text-left text-label-md transition-colors ${
                        activeWorkspaceId === workspace.id
                          ? 'bg-primary-fixed text-primary'
                          : 'text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface'
                      }`}
                      aria-current={activeWorkspaceId === workspace.id ? 'true' : undefined}
                    >
                      {workspace.name}
                    </button>
                  </li>
                ))}
              </ul>
            </details>
          )
        })}

        {showAdminNav ? (
          <Link
            to="/app/admin"
            onClick={onNavigate}
            className={navLinkClass(location.pathname.startsWith('/app/admin'))}
          >
            <span
              className={
                location.pathname.startsWith('/app/admin') ? 'text-primary' : 'text-on-surface-variant'
              }
            >
              {ICONS.admin}
            </span>
            <span>Admin</span>
          </Link>
        ) : null}
      </nav>

      <div className="mt-4 flex flex-col gap-1 border-t border-outline-variant pt-3" aria-label="Account actions">
        <Link to="/app/settings" onClick={onNavigate} className={navLinkClass(false)}>
          <span className="text-on-surface-variant">{ICONS.support}</span>
          <span>Support</span>
        </Link>
        <button
          type="button"
          onClick={onSignOut}
          disabled={!onSignOut || signOutPending}
          className={`${navLinkClass(false)} w-full disabled:cursor-not-allowed disabled:opacity-60`}
          data-testid="sidenav-sign-out"
        >
          <span className="text-on-surface-variant">{ICONS.signOut}</span>
          <span>{signOutPending ? 'Signing out…' : 'Sign Out'}</span>
        </button>
      </div>
    </aside>
  )
}

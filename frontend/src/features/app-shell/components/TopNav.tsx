import { useMemo } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { NotificationBell } from '../../notifications/components/NotificationBell'
import { useMediaQuery } from '../../../shared/hooks/useMediaQuery'
import type { AppShellTopProps } from '../types'
import { UserMenu } from './UserMenu'

const SECONDARY_NAV = [
  { id: 'drafts', label: 'Drafts', to: '/app/search?filter=drafts' },
  { id: 'shared', label: 'Shared', to: '/app/search?filter=shared' },
  { id: 'archived', label: 'Archived', to: '/app/search?filter=archived' },
] as const

/**
 * Faz 151C TopNav — matches docs/design/workspace_dashboard_empty header:
 * rounded-full search pill (left), Drafts/Shared/Archived secondary nav,
 * Invite Team CTA, and avatar/menu (right).
 *
 * Drafts/Shared/Archived deep-link into the existing global search route via
 * the `filter` query param so we never break routing or invent endpoints.
 */
export function TopNav({
  search,
  onSearchChange,
  onSidebarToggle,
  isOnline = true,
  onOpenSearch,
  onInviteTeam,
  inviteTeamDisabled,
}: AppShellTopProps) {
  const isMobile = useMediaQuery('(max-width: 639px)')
  const isLargeScreen = useMediaQuery('(min-width: 1024px)')
  const location = useLocation()

  const activeSecondaryFilter = useMemo(() => {
    if (!location.pathname.startsWith('/app/search')) return null
    const params = new URLSearchParams(location.search)
    return params.get('filter')
  }, [location.pathname, location.search])

  return (
    <header
      className="sticky top-0 z-30 border-b border-outline-variant bg-surface/85 backdrop-blur"
      data-testid="topnav"
    >
      {!isOnline ? (
        <p
          className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-label-md text-amber-900"
          role="status"
        >
          You are offline. Showing cached content where available.
        </p>
      ) : null}
      <div className="flex h-16 items-center justify-between gap-3 px-4 sm:gap-4 sm:px-6">
        <div className="flex flex-1 items-center gap-3">
          <button
            type="button"
            className="rounded-lg border border-outline-variant bg-surface-container-lowest px-2 py-1 text-body-md hover:bg-surface-container-low focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary lg:hidden"
            aria-label="Open navigation menu"
            data-testid="sidebar-toggle"
            onClick={onSidebarToggle}
          >
            Menu
          </button>

          {isMobile ? (
            <button
              type="button"
              aria-label="Open search"
              className="flex items-center gap-2 rounded-full border border-outline-variant bg-surface-container-lowest px-3 py-1.5 text-label-md text-on-surface-variant hover:bg-surface-container-low"
              onClick={onOpenSearch}
            >
              <SearchIcon />
              Search
            </button>
          ) : (
            <label
              htmlFor="topbar-search"
              className="flex w-full max-w-md items-center gap-2 rounded-full border border-outline-variant bg-surface-container-lowest px-4 py-1.5 focus-within:border-primary focus-within:ring-1 focus-within:ring-primary"
            >
              <SearchIcon />
              <span className="sr-only">Search notes</span>
              <input
                id="topbar-search"
                type="text"
                placeholder="Search workspace…"
                value={search}
                onChange={(event) => onSearchChange(event.target.value)}
                onFocus={() => onOpenSearch?.()}
                className="w-full border-0 bg-transparent text-body-md text-on-surface placeholder:text-on-surface-variant/70 focus:outline-none focus:ring-0"
              />
              <kbd className="hidden rounded border border-outline-variant bg-surface-container px-1.5 py-0.5 text-label-md text-on-surface-variant md:inline">
                Ctrl+K
              </kbd>
            </label>
          )}
        </div>

        <div className="flex items-center gap-4">
          {isLargeScreen ? (
            <nav className="flex items-center gap-5 text-body-md" aria-label="Secondary views">
              {SECONDARY_NAV.map((item) => {
                const isActive = activeSecondaryFilter === item.id
                return (
                  <Link
                    key={item.id}
                    to={item.to}
                    className={`relative transition-colors hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
                      isActive ? 'font-semibold text-primary' : 'text-secondary'
                    }`}
                  >
                    {item.label}
                  </Link>
                )
              })}
            </nav>
          ) : null}

          <div className="flex items-center gap-2 border-l border-outline-variant pl-4">
            <NotificationBell />
            <button
              type="button"
              onClick={onInviteTeam}
              disabled={inviteTeamDisabled || !onInviteTeam}
              className="rounded-full bg-primary-container px-4 py-1.5 text-label-md font-semibold text-on-primary transition-colors hover:bg-primary-container/80 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60"
              data-testid="topnav-invite-team"
              aria-label={inviteTeamDisabled ? 'Invite team — workspace required' : 'Invite team'}
            >
              Invite Team
            </button>
            <UserMenu />
          </div>
        </div>
      </div>
    </header>
  )
}

function SearchIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      aria-hidden
      className="h-4 w-4 text-on-surface-variant"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
    >
      <circle cx="11" cy="11" r="6" />
      <path d="m20 20-3.5-3.5" strokeLinecap="round" />
    </svg>
  )
}

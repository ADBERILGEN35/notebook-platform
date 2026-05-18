import { Input } from '../../../shared/components/Input'
import { Button } from '../../../shared/components/Button'
import { NotificationBell } from '../../notifications/components/NotificationBell'
import { useMediaQuery } from '../../../shared/hooks/useMediaQuery'
import type { AppShellTopProps } from '../types'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'
import { UserMenu } from './UserMenu'

export function TopNav({
  search,
  onSearchChange,
  onCreateNote,
  onSidebarToggle,
  isOnline = true,
  workspaces,
  activeWorkspaceId,
  onWorkspaceSelect,
  onOpenSearch,
}: AppShellTopProps) {
  const isMobile = useMediaQuery('(max-width: 639px)')

  return (
    <header className="sticky top-0 z-10 border-b border-outline-variant bg-surface-container-lowest/95 backdrop-blur">
      {!isOnline ? (
        <p className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-label-md text-amber-900" role="status">
          You are offline. Showing cached content where available.
        </p>
      ) : null}
      <div className="flex flex-wrap items-center gap-2 px-3 py-2 sm:gap-3 sm:px-4 sm:py-3">
        <button
          type="button"
          className="rounded-lg border border-outline-variant bg-surface-container-lowest px-2 py-1 text-body-md hover:bg-surface-container-low focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary lg:hidden"
          aria-label="Open navigation menu"
          data-testid="sidebar-toggle"
          onClick={onSidebarToggle}
        >
          Menu
        </button>

        {!isMobile ? (
          <WorkspaceSwitcher
            workspaces={workspaces}
            activeWorkspaceId={activeWorkspaceId}
            onSelect={onWorkspaceSelect}
            className="hidden sm:flex"
          />
        ) : null}

        {isMobile ? (
          <button
            type="button"
            aria-label="Open search"
            className="rounded-lg border border-outline-variant px-2 py-1 text-body-md hover:bg-surface-container-low"
            onClick={onOpenSearch}
          >
            Search
          </button>
        ) : (
          <div className="min-w-[12rem] flex-1">
            <label className="sr-only" htmlFor="topbar-search">
              Search notes
            </label>
            <Input
              id="topbar-search"
              placeholder="Search notes… (Ctrl+K)"
              value={search}
              onChange={(event) => onSearchChange(event.target.value)}
              onFocus={() => onOpenSearch?.()}
            />
          </div>
        )}

        <Button
          className="bg-primary px-3 py-2 text-white hover:bg-primary-container"
          onClick={onCreateNote}
          type="button"
        >
          {isMobile ? 'Create' : 'Create note'}
        </Button>
        <NotificationBell />
        <UserMenu />
      </div>
    </header>
  )
}

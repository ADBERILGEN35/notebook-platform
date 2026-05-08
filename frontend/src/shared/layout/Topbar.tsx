import { Link } from 'react-router-dom'
import { Input } from '../components/Input'
import { Button } from '../components/Button'
import { NotificationBell } from '../../features/notifications/components/NotificationBell'
import { useMediaQuery } from '../hooks/useMediaQuery'

type Props = {
  search: string
  onSearchChange: (value: string) => void
  onCreateNote: () => void
  onSidebarToggle?: () => void
  isOnline?: boolean
}

export function Topbar({ search, onSearchChange, onCreateNote, onSidebarToggle, isOnline = true }: Props) {
  const isMobile = useMediaQuery('(max-width: 639px)')
  return (
    <header className="flex flex-wrap items-center gap-2 border-b border-slate-200 bg-white px-3 py-2 sm:px-4 sm:py-3">
      {!isOnline ? (
        <div className="w-full rounded border border-amber-300 bg-amber-50 px-2 py-1 text-xs text-amber-800">
          You are offline. Showing cached content where available.
        </div>
      ) : null}
      <button
        type="button"
        className="rounded border border-slate-300 bg-white px-2 py-1 text-sm hover:bg-slate-50 lg:hidden"
        aria-label="Open navigation menu"
        data-testid="sidebar-toggle"
        onClick={onSidebarToggle}
      >
        Menu
      </button>
      {isMobile ? (
        <Link
          to="/app/search"
          aria-label="Open search page"
          className="rounded border border-slate-300 bg-white px-2 py-1 text-sm hover:bg-slate-50"
        >
          Search
        </Link>
      ) : (
        <div className="min-w-[14rem] flex-1">
          <Input
            placeholder="Search notes..."
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
          />
        </div>
      )}
      <Button className="bg-primary-600 px-2 py-1 text-white hover:bg-primary-700 sm:px-3 sm:py-2" onClick={onCreateNote}>
        {isMobile ? 'Create' : 'Create note'}
      </Button>
      <NotificationBell />
      <Link to="/app/settings/security" className="text-xs text-slate-600 sm:text-sm">
        Security
      </Link>
    </header>
  )
}


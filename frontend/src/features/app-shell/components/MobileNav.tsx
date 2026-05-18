import { Link, useLocation } from 'react-router-dom'

type MobileNavProps = {
  onOpenMenu: () => void
}

export function MobileNav({ onOpenMenu }: MobileNavProps) {
  const location = useLocation()
  const itemClass = (active: boolean) =>
    `flex flex-1 flex-col items-center gap-1 py-2 text-label-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary ${
      active ? 'text-primary font-medium' : 'text-on-surface-variant'
    }`

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-20 flex border-t border-outline-variant bg-surface-container-lowest lg:hidden"
      aria-label="Mobile navigation"
    >
      <Link to="/app/workspaces" className={itemClass(location.pathname.startsWith('/app/workspaces') || location.pathname === '/app')} aria-label="Workspaces">
        Hub
      </Link>
      <Link to="/app/search" className={itemClass(location.pathname === '/app/search')} aria-label="Search">
        Search
      </Link>
      <Link to="/app/notifications" className={itemClass(location.pathname === '/app/notifications')} aria-label="Notifications">
        Alerts
      </Link>
      <button type="button" className={itemClass(false)} onClick={onOpenMenu} aria-label="Open menu">
        More
      </button>
    </nav>
  )
}

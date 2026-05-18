import { NavLink } from 'react-router-dom'

const links: Array<{ to: string; label: string; end?: boolean }> = [
  { to: '/app/settings', label: 'Profile', end: true },
  { to: '/app/settings/security', label: 'Security & sessions' },
  { to: '/app/settings/notifications', label: 'Notifications' },
  { to: '/app/settings/sync', label: 'Offline & sync' },
]

export function SettingsNav() {
  return (
    <nav aria-label="Settings" className="flex flex-col gap-1 border-b border-outline-variant pb-4 lg:border-b-0 lg:border-r lg:pb-0 lg:pr-6">
      {links.map((link) => (
        <NavLink
          key={link.to}
          to={link.to}
          end={link.end}
          className={({ isActive }) =>
            `rounded-lg px-3 py-2 text-body-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary ${
              isActive ? 'bg-primary-fixed font-medium text-primary' : 'text-on-surface-variant hover:bg-surface-container-low'
            }`
          }
        >
          {link.label}
        </NavLink>
      ))}
    </nav>
  )
}

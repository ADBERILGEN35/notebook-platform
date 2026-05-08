import { useState } from 'react'
import { isNotificationsEnabled } from '../../../shared/config/notifications-feature-flags'
import { useUnreadNotificationCount } from '../notification-hooks'
import { NotificationDropdown } from './NotificationDropdown'
import { useMediaQuery } from '../../../shared/hooks/useMediaQuery'

export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const enabled = isNotificationsEnabled()
  const isMobile = useMediaQuery('(max-width: 639px)')
  const unreadCount = useUnreadNotificationCount(enabled)
  if (!enabled) return null

  return (
    <div className="relative">
      <button
        type="button"
        className="relative rounded border border-slate-300 bg-white px-2 py-1 text-xs hover:bg-slate-50 sm:px-3 sm:py-2 sm:text-sm"
        onClick={() => setOpen((v) => !v)}
        aria-label="Notifications"
      >
        {isMobile ? 'Notif' : 'Bell'}
        {(unreadCount.data?.unreadCount ?? 0) > 0 ? (
          <span className="absolute -right-2 -top-2 rounded-full bg-rose-600 px-1.5 py-0.5 text-[10px] font-bold text-white">
            {unreadCount.data?.unreadCount}
          </span>
        ) : null}
      </button>
      <NotificationDropdown open={open} onClose={() => setOpen(false)} />
    </div>
  )
}

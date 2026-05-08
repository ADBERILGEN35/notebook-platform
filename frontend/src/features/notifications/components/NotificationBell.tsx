import { useState } from 'react'
import { isNotificationsEnabled } from '../../../shared/config/notifications-feature-flags'
import { useUnreadNotificationCount } from '../notification-hooks'
import { NotificationDropdown } from './NotificationDropdown'

export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const enabled = isNotificationsEnabled()
  const unreadCount = useUnreadNotificationCount(enabled)
  if (!enabled) return null

  return (
    <div className="relative">
      <button
        type="button"
        className="relative rounded border border-slate-300 bg-white px-3 py-2 text-sm hover:bg-slate-50"
        onClick={() => setOpen((v) => !v)}
        aria-label="Notifications"
      >
        Bell
        {(unreadCount.data?.unreadCount ?? 0) > 0 ? (
          <span className="absolute -right-2 -top-2 rounded-full bg-rose-600 px-1.5 py-0.5 text-[10px] font-bold text-white">
            {unreadCount.data?.unreadCount}
          </span>
        ) : null}
      </button>
      <NotificationDropdown open={open} />
    </div>
  )
}

import type { UserNotification } from '../notifications-types'
import { NotificationItem } from './NotificationItem'

export function NotificationList({
  notifications,
  onMarkRead,
  onArchive,
}: {
  notifications: UserNotification[]
  onMarkRead: (id: string) => void
  onArchive: (id: string) => void
}) {
  return (
    <div className="space-y-2">
      {notifications.map((notification) => (
        <NotificationItem
          key={notification.id}
          notification={notification}
          onMarkRead={onMarkRead}
          onArchive={onArchive}
        />
      ))}
    </div>
  )
}

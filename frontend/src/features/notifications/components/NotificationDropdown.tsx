import { Link } from 'react-router-dom'
import { Card } from '../../../shared/components/Card'
import { LoadingState } from '../../../shared/components/LoadingState'
import { ErrorAlert } from '../../../shared/components/ErrorAlert'
import { NotificationItem } from './NotificationItem'
import { useArchiveNotification, useMarkNotificationRead, useNotifications } from '../notification-hooks'

export function NotificationDropdown({ open }: { open: boolean }) {
  const filters = { page: 0, size: 5, sort: 'createdAt,desc', unreadOnly: false as boolean }
  const query = useNotifications(filters, open)
  const markRead = useMarkNotificationRead()
  const archive = useArchiveNotification()

  if (!open) return null

  return (
    <div className="absolute right-0 top-10 z-30 w-[26rem]">
      <Card className="space-y-3 p-3">
        <div className="flex items-center justify-between">
          <p className="text-sm font-semibold text-slate-900">Notifications</p>
          <Link to="/app/notifications" className="text-xs text-primary-700 hover:underline">
            View all
          </Link>
        </div>
        {query.isLoading ? <LoadingState /> : null}
        {query.isError ? <ErrorAlert error={query.error} /> : null}
        {query.data && query.data.items.length === 0 ? (
          <p className="rounded border border-dashed border-slate-300 p-3 text-sm text-slate-500">No notifications yet.</p>
        ) : null}
        <div className="max-h-[28rem] space-y-2 overflow-auto">
          {query.data?.items.map((n) => (
            <NotificationItem
              key={n.id}
              notification={n}
              compact
              onMarkRead={(id) => markRead.mutate(id)}
              onArchive={(id) => archive.mutate(id)}
            />
          ))}
        </div>
      </Card>
    </div>
  )
}

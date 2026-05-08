import { Link } from 'react-router-dom'
import { Card } from '../../../shared/components/Card'
import { LoadingState } from '../../../shared/components/LoadingState'
import { ErrorAlert } from '../../../shared/components/ErrorAlert'
import { NotificationItem } from './NotificationItem'
import { useArchiveNotification, useMarkNotificationRead, useNotifications } from '../notification-hooks'
import { useMediaQuery } from '../../../shared/hooks/useMediaQuery'

export function NotificationDropdown({ open, onClose }: { open: boolean; onClose?: () => void }) {
  const filters = { page: 0, size: 5, sort: 'createdAt,desc', unreadOnly: false as boolean }
  const query = useNotifications(filters, open)
  const markRead = useMarkNotificationRead()
  const archive = useArchiveNotification()
  const isMobile = useMediaQuery('(max-width: 639px)')

  if (!open) return null

  if (isMobile) {
    return (
      <>
        <div className="fixed inset-0 z-30 bg-slate-900/40" onClick={onClose} aria-hidden />
        <div className="fixed inset-x-0 bottom-0 z-40 max-h-[70vh] overflow-y-auto rounded-t-xl border border-slate-200 bg-white p-3">
          <div className="mb-2 flex items-center justify-between">
            <p className="text-sm font-semibold text-slate-900">Notifications</p>
            <button type="button" className="text-xs text-slate-600" onClick={onClose}>
              Close
            </button>
          </div>
          <Link to="/app/notifications" className="mb-2 block text-xs text-primary-700 hover:underline" onClick={onClose}>
            View all
          </Link>
          <Link to="/app/settings/notifications" className="mb-2 block text-xs text-primary-700 hover:underline" onClick={onClose}>
            Notification settings
          </Link>
          {query.isLoading ? <LoadingState /> : null}
          {query.isError ? <ErrorAlert error={query.error} /> : null}
          <div className="space-y-2">
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
        </div>
      </>
    )
  }

  return (
    <div className="absolute right-0 top-10 z-30 w-[26rem]">
      <Card className="space-y-3 p-3">
        <div className="flex items-center justify-between">
          <p className="text-sm font-semibold text-slate-900">Notifications</p>
          <Link to="/app/notifications" className="text-xs text-primary-700 hover:underline" onClick={onClose}>
            View all
          </Link>
        </div>
        <Link to="/app/settings/notifications" className="text-xs text-primary-700 hover:underline" onClick={onClose}>
          Notification settings
        </Link>
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

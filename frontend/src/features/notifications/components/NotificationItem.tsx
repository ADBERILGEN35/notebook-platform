import { Link } from 'react-router-dom'
import type { UserNotification } from '../notifications-types'
import { Button } from '../../../shared/components/Button'

function isSafeActionUrl(actionUrl?: string | null): actionUrl is string {
  if (!actionUrl) return false
  const normalized = actionUrl.trim().toLowerCase()
  return normalized.startsWith('/app/') && !normalized.startsWith('/app/javascript:')
}

function relativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime()
  const minutes = Math.max(1, Math.floor(diffMs / 60_000))
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

const severityColor: Record<UserNotification['severity'], string> = {
  INFO: 'bg-sky-100 text-sky-700',
  SUCCESS: 'bg-emerald-100 text-emerald-700',
  WARNING: 'bg-amber-100 text-amber-700',
  CRITICAL: 'bg-rose-100 text-rose-700',
}

export function NotificationItem({
  notification,
  onMarkRead,
  onArchive,
  compact = false,
}: {
  notification: UserNotification
  onMarkRead: (id: string) => void
  onArchive: (id: string) => void
  compact?: boolean
}) {
  return (
    <article
      className={`rounded-md border p-3 ${notification.unread ? 'border-primary-300 bg-primary-50/30' : 'border-slate-200 bg-white'}`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className={`rounded px-2 py-0.5 text-[10px] font-semibold ${severityColor[notification.severity]}`}>
              {notification.severity}
            </span>
            <span className="text-[11px] text-slate-500">{relativeTime(notification.createdAt)}</span>
          </div>
          <p className="text-sm font-semibold text-slate-900">{notification.title}</p>
          <p className="text-sm text-slate-700">{notification.message}</p>
        </div>
      </div>
      <div className="mt-2 flex flex-wrap gap-2">
        {notification.unread ? (
          <Button type="button" className="text-xs" onClick={() => onMarkRead(notification.id)}>
            Mark read
          </Button>
        ) : null}
        <Button type="button" className="text-xs" onClick={() => onArchive(notification.id)}>
          Archive
        </Button>
        {isSafeActionUrl(notification.actionUrl) ? (
          <Link className="text-xs text-primary-700 hover:underline" to={notification.actionUrl}>
            Open
          </Link>
        ) : null}
      </div>
      {!compact ? <p className="mt-1 text-[11px] text-slate-400">{notification.type}</p> : null}
    </article>
  )
}

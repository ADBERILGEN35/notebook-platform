import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '../shared/components/PageHeader'
import { Card } from '../shared/components/Card'
import { Button } from '../shared/components/Button'
import { Input } from '../shared/components/Input'
import { LoadingState } from '../shared/components/LoadingState'
import { EmptyState } from '../shared/components/EmptyState'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { PaginationControls } from '../shared/components/PaginationControls'
import {
  useArchiveNotification,
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
  useUnreadNotificationCount,
} from '../features/notifications/notification-hooks'
import { NotificationList } from '../features/notifications/components/NotificationList'
import type { UserNotificationType } from '../features/notifications/notifications-types'
import { isNotificationsEnabled } from '../shared/config/notifications-feature-flags'
import { PermissionDenied } from '../shared/components/PermissionDenied'
import { useMediaQuery } from '../shared/hooks/useMediaQuery'

const TYPES: UserNotificationType[] = [
  'WORKSPACE_INVITATION_RECEIVED',
  'COMMENT_ADDED',
  'NOTE_VERSION_RESTORED',
  'SECURITY_SESSIONS_REVOKED',
  'SYSTEM_NOTICE',
]

export function NotificationsPage() {
  const enabled = isNotificationsEnabled()
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [type, setType] = useState<UserNotificationType | ''>('')
  const [workspaceId, setWorkspaceId] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [showFilters, setShowFilters] = useState(false)
  const isMobile = useMediaQuery('(max-width: 639px)')

  const filters = { unreadOnly, type, workspaceId: workspaceId || undefined, page, size, sort: 'createdAt,desc' }
  const notifications = useNotifications(filters, enabled)
  const unread = useUnreadNotificationCount(enabled)
  const markRead = useMarkNotificationRead()
  const markAll = useMarkAllNotificationsRead()
  const archive = useArchiveNotification()

  if (!enabled) {
    return (
      <PermissionDenied
        title="Notifications disabled"
        message="In-app notifications are currently disabled in this environment."
      />
    )
  }

  return (
    <div className="space-y-4">
      <PageHeader title="Notifications" subtitle="Polling-based notification center MVP." />
      <Link to="/app/settings/notifications" className="inline-block text-sm text-primary-700 hover:underline">
        Notification settings
      </Link>
      <Card className="space-y-3">
        {isMobile ? (
          <Button type="button" className="text-xs" onClick={() => setShowFilters((v) => !v)}>
            {showFilters ? 'Hide filters' : 'Show filters'}
          </Button>
        ) : null}
        <div className={`flex flex-wrap items-center gap-3 ${isMobile && !showFilters ? 'hidden' : ''}`}>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} />
            Unread only
          </label>
          <select
            className="rounded border border-slate-200 px-2 py-1 text-sm"
            value={type}
            onChange={(e) => setType(e.target.value as UserNotificationType | '')}
          >
            <option value="">All types</option>
            {TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
          <Input
            placeholder="workspaceId (optional)"
            value={workspaceId}
            onChange={(event) => setWorkspaceId(event.target.value)}
          />
          <select
            className="rounded border border-slate-200 px-2 py-1 text-sm"
            value={size}
            onChange={(e) => setSize(Number(e.target.value))}
          >
            {[20, 50, 100].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
          <Button type="button" className="text-xs" onClick={() => markAll.mutate(workspaceId || undefined)}>
            Mark all as read
          </Button>
        </div>
        <p className="text-xs text-slate-500">Unread count: {unread.data?.unreadCount ?? 0}</p>
      </Card>

      {notifications.isLoading ? <LoadingState /> : null}
      {notifications.isError ? <ErrorAlert error={notifications.error} /> : null}
      {notifications.data && notifications.data.items.length === 0 ? (
        <EmptyState title="No notifications" message="You're all caught up." />
      ) : null}
      {notifications.data && notifications.data.items.length > 0 ? (
        <>
          <NotificationList
            notifications={notifications.data.items}
            onMarkRead={(id) => markRead.mutate(id)}
            onArchive={(id) => archive.mutate(id)}
          />
          <PaginationControls
            page={notifications.data.page}
            hasNext={notifications.data.hasNext}
            hasPrevious={notifications.data.hasPrevious}
            onNext={() => setPage((p) => p + 1)}
            onPrevious={() => setPage((p) => Math.max(0, p - 1))}
          />
        </>
      ) : null}
    </div>
  )
}

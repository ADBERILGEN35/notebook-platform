import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '../shared/components/PageHeader'
import { ResponsiveContent } from '../shared/components/ResponsiveContent'
import { SectionCard } from '../shared/components/SectionCard'
import { Button } from '../shared/components/Button'
import { Input } from '../shared/components/Input'
import { LoadingState } from '../shared/components/LoadingState'
import { EmptyState } from '../shared/components/EmptyState'
import { ErrorState } from '../shared/components/ErrorState'
import { InlineStatus } from '../shared/components/InlineStatus'
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

export function NotificationCenterPage() {
  const enabled = isNotificationsEnabled()
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [type, setType] = useState<UserNotificationType | ''>('')
  const [workspaceId, setWorkspaceId] = useState('')
  const [page, setPage] = useState(0)
  const [size] = useState(20)
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
      <ResponsiveContent>
        <PermissionDenied
          title="Notifications disabled"
          message="In-app notifications are currently disabled in this environment."
        />
      </ResponsiveContent>
    )
  }

  return (
    <ResponsiveContent>
      <PageHeader
        title="Notification center"
        subtitle="Stay on top of invites, comments, and security events."
        actions={
          unread.data !== undefined ? <InlineStatus label={`${unread.data} unread`} tone={unread.data ? 'warning' : 'neutral'} /> : null
        }
      />
      <Link to="/app/settings/notifications" className="mb-4 inline-block text-body-md text-primary hover:underline">
        Notification preferences
      </Link>

      <SectionCard title="Inbox">
        {isMobile ? (
          <Button type="button" className="mb-3 text-label-md" onClick={() => setShowFilters((v) => !v)}>
            {showFilters ? 'Hide filters' : 'Show filters'}
          </Button>
        ) : null}
        <div className={`mb-4 flex flex-wrap items-center gap-3 ${isMobile && !showFilters ? 'hidden' : ''}`}>
          <label className="flex items-center gap-2 text-body-md text-on-surface-variant">
            <input type="checkbox" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} />
            Unread only
          </label>
          <select
            className="rounded-lg border border-outline-variant px-2 py-1 text-body-md"
            value={type}
            onChange={(e) => setType(e.target.value as UserNotificationType | '')}
            aria-label="Notification type"
          >
            <option value="">All types</option>
            {TYPES.map((t) => (
              <option key={t} value={t}>
                {t.replace(/_/g, ' ')}
              </option>
            ))}
          </select>
          <Input
            placeholder="Workspace ID filter"
            value={workspaceId}
            onChange={(e) => setWorkspaceId(e.target.value)}
            className="max-w-xs"
            aria-label="Workspace ID"
          />
          <Button type="button" onClick={() => markAll.mutate(workspaceId || undefined)} disabled={markAll.isPending}>
            Mark all read
          </Button>
        </div>

        {notifications.isLoading ? <LoadingState label="Loading notifications…" /> : null}
        {notifications.isError ? <ErrorState error={notifications.error} /> : null}
        {notifications.data?.items.length === 0 && !notifications.isLoading ? (
          <EmptyState title="All caught up" message="No notifications match your filters." />
        ) : null}
        {notifications.data?.items.length ? (
          <NotificationList
            notifications={notifications.data.items}
            onMarkRead={(id) => markRead.mutate(id)}
            onArchive={(id) => archive.mutate(id)}
          />
        ) : null}
        {notifications.data ? (
          <PaginationControls
            page={page}
            hasNext={notifications.data.hasNext}
            hasPrevious={notifications.data.hasPrevious}
            onNext={() => setPage((p) => p + 1)}
            onPrevious={() => setPage((p) => Math.max(0, p - 1))}
          />
        ) : null}
      </SectionCard>
    </ResponsiveContent>
  )
}

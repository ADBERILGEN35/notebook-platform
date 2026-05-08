import type { QueryClient } from '@tanstack/react-query'
import { notificationsKeys } from './notification-hooks'
import { isCookieMode } from '../../shared/config/auth-transport'
import { isNotificationsEnabled, isNotificationsSseEnabled } from '../../shared/config/notifications-feature-flags'
import { API_BASE_URL } from '../../shared/api/api-client'

type UnreadCountPayload = { unreadCount?: number }

const invalidateNotifications = async (queryClient: QueryClient) => {
  await queryClient.invalidateQueries({ queryKey: notificationsKeys.unreadCount() })
  await queryClient.invalidateQueries({ queryKey: ['notifications'] })
}

export function connectNotificationEventStream(queryClient: QueryClient): EventSource | null {
  if (!isNotificationsEnabled() || !isNotificationsSseEnabled()) return null
  if (!isCookieMode() || typeof window === 'undefined' || typeof window.EventSource === 'undefined') return null

  const source = new EventSource(`${API_BASE_URL}/notifications/stream`, { withCredentials: true })
  const handleInvalidation = () => void invalidateNotifications(queryClient)
  const handleUnreadCount = (event: MessageEvent<string>) => {
    try {
      const payload = JSON.parse(event.data) as UnreadCountPayload
      if (typeof payload.unreadCount === 'number') {
        queryClient.setQueryData(notificationsKeys.unreadCount(), { unreadCount: payload.unreadCount })
      }
    } catch {
      // ignore malformed events and keep stream alive
    }
    void invalidateNotifications(queryClient)
  }

  source.addEventListener('notification.created', handleInvalidation)
  source.addEventListener('notification.read', handleInvalidation)
  source.addEventListener('notification.archived', handleInvalidation)
  source.addEventListener('notification.unread_count', handleUnreadCount)
  source.addEventListener('heartbeat', () => {})

  return source
}

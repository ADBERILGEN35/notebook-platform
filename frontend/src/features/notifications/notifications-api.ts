import { apiRequest } from '../../shared/api/api-client'
import type { NotificationsPageResponse, NotificationsQueryFilters } from './notifications-types'

export function notificationsQueryString(filters: NotificationsQueryFilters): string {
  const params = new URLSearchParams()
  if (filters.unreadOnly) params.set('unreadOnly', 'true')
  if (filters.type) params.set('type', filters.type)
  if (filters.workspaceId) params.set('workspaceId', filters.workspaceId)
  params.set('page', String(filters.page))
  params.set('size', String(filters.size))
  params.set('sort', filters.sort || 'createdAt,desc')
  return params.toString()
}

export async function listNotifications(filters: NotificationsQueryFilters): Promise<NotificationsPageResponse> {
  return apiRequest<NotificationsPageResponse>(`/notifications?${notificationsQueryString(filters)}`, {
    method: 'GET',
  })
}

export async function fetchUnreadCount(): Promise<{ unreadCount: number }> {
  return apiRequest<{ unreadCount: number }>('/notifications/unread-count', { method: 'GET' })
}

export async function markNotificationRead(notificationId: string) {
  return apiRequest(`/notifications/${notificationId}/read`, { method: 'POST', body: JSON.stringify({}) })
}

export async function markAllNotificationsRead(workspaceId?: string) {
  return apiRequest<{ updatedCount: number }>('/notifications/read-all', {
    method: 'POST',
    body: JSON.stringify(workspaceId ? { workspaceId } : {}),
  })
}

export async function archiveNotification(notificationId: string) {
  return apiRequest(`/notifications/${notificationId}/archive`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
}

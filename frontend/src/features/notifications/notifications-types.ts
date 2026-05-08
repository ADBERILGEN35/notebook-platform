import type { PageResponse } from '../../shared/types/api'

export type UserNotificationType =
  | 'WORKSPACE_INVITATION_RECEIVED'
  | 'COMMENT_ADDED'
  | 'NOTE_VERSION_RESTORED'
  | 'SECURITY_SESSIONS_REVOKED'
  | 'SYSTEM_NOTICE'

export type UserNotificationSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL'

export type UserNotification = {
  id: string
  workspaceId?: string | null
  type: UserNotificationType
  title: string
  message: string
  severity: UserNotificationSeverity
  actionUrl?: string | null
  unread: boolean
  readAt?: string | null
  createdAt: string
}

export type NotificationsQueryFilters = {
  unreadOnly?: boolean
  type?: UserNotificationType | ''
  workspaceId?: string
  page: number
  size: number
  sort: string
}

export type NotificationsPageResponse = PageResponse<UserNotification>

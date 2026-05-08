import { apiRequest } from '../../shared/api/api-client'

export type NotificationChannel = 'IN_APP' | 'EMAIL'
export type UserNotificationType =
  | 'WORKSPACE_INVITATION_RECEIVED'
  | 'COMMENT_ADDED'
  | 'NOTE_VERSION_RESTORED'
  | 'SECURITY_SESSIONS_REVOKED'
  | 'SYSTEM_NOTICE'

export interface NotificationPreferenceItem {
  notificationType: UserNotificationType
  label: string
  description: string
  channels: Record<NotificationChannel, { enabled: boolean; mandatory: boolean }>
}

export async function getNotificationPreferences(): Promise<NotificationPreferenceItem[]> {
  return apiRequest<NotificationPreferenceItem[]>('/notification-preferences', { method: 'GET' })
}

export async function patchNotificationPreferences(
  updates: Array<{ notificationType: UserNotificationType; channel: NotificationChannel; enabled: boolean }>,
): Promise<NotificationPreferenceItem[]> {
  return apiRequest<NotificationPreferenceItem[]>('/notification-preferences', {
    method: 'PATCH',
    body: JSON.stringify({ updates }),
  })
}

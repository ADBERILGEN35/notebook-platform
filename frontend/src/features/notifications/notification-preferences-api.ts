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

export type EmailDigestFrequency = 'NEVER' | 'DAILY' | 'WEEKLY'

export interface NotificationDeliveryPreference {
  emailDigestEnabled: boolean
  emailDigestFrequency: EmailDigestFrequency
  quietHoursEnabled: boolean
  quietHoursStart: string | null
  quietHoursEnd: string | null
  timezone: string
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

export async function getNotificationDeliveryPreferences(): Promise<NotificationDeliveryPreference> {
  return apiRequest<NotificationDeliveryPreference>('/notification-delivery-preferences', { method: 'GET' })
}

export async function patchNotificationDeliveryPreferences(
  payload: NotificationDeliveryPreference,
): Promise<NotificationDeliveryPreference> {
  return apiRequest<NotificationDeliveryPreference>('/notification-delivery-preferences', {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

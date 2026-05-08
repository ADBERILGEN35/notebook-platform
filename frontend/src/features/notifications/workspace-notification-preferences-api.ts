import { apiRequest } from '../../shared/api/api-client'
import type { NotificationChannel, UserNotificationType } from './notification-preferences-api'

export type WorkspaceChannelState = {
  enabled: boolean | null
  inherited: boolean
  effectiveEnabled: boolean
  mandatory: boolean
}

export type WorkspaceNotificationPreferenceRow = {
  notificationType: UserNotificationType
  label: string
  description: string
  channels: Record<NotificationChannel, WorkspaceChannelState>
}

export type WorkspaceNotificationPreferencesResponse = {
  workspaceId: string
  preferences: WorkspaceNotificationPreferenceRow[]
}

export type WorkspacePreferenceUpdate = {
  notificationType: UserNotificationType
  channel: NotificationChannel
  inheritGlobal: boolean
  enabled?: boolean
}

export async function getWorkspaceNotificationPreferences(
  workspaceId: string,
): Promise<WorkspaceNotificationPreferencesResponse> {
  return apiRequest<WorkspaceNotificationPreferencesResponse>(
    `/notification-preferences/workspaces/${encodeURIComponent(workspaceId)}`,
    { method: 'GET' },
  )
}

export async function patchWorkspaceNotificationPreferences(
  workspaceId: string,
  updates: WorkspacePreferenceUpdate[],
): Promise<WorkspaceNotificationPreferencesResponse> {
  return apiRequest<WorkspaceNotificationPreferencesResponse>(
    `/notification-preferences/workspaces/${encodeURIComponent(workspaceId)}`,
    { method: 'PATCH', body: JSON.stringify({ updates }) },
  )
}

export async function resetWorkspaceNotificationPreferences(
  workspaceId: string,
): Promise<WorkspaceNotificationPreferencesResponse> {
  return apiRequest<WorkspaceNotificationPreferencesResponse>(
    `/notification-preferences/workspaces/${encodeURIComponent(workspaceId)}/reset`,
    { method: 'POST' },
  )
}

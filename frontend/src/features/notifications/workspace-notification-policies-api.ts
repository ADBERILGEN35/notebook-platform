import { apiRequest } from '../../shared/api/api-client'
import type { NotificationChannel, UserNotificationType } from './notification-preferences-api'

export type WorkspaceNotificationPolicyMode =
  | 'USER_CONTROLLED'
  | 'FORCE_ENABLED'
  | 'FORCE_DISABLED'

export type WorkspaceChannelPolicyState = {
  policyMode: WorkspaceNotificationPolicyMode
  reason: string | null
  manageable: boolean
}

export type WorkspaceNotificationPolicyRow = {
  notificationType: UserNotificationType
  label: string
  channels: Record<NotificationChannel, WorkspaceChannelPolicyState>
}

export type WorkspaceNotificationPoliciesResponse = {
  workspaceId: string
  canManagePolicies: boolean
  policies: WorkspaceNotificationPolicyRow[]
}

export type WorkspaceNotificationPolicyUpdate = {
  notificationType: UserNotificationType
  channel: NotificationChannel
  policyMode: WorkspaceNotificationPolicyMode
  reason?: string | null
}

export async function getWorkspaceNotificationPolicies(
  workspaceId: string,
): Promise<WorkspaceNotificationPoliciesResponse> {
  return apiRequest<WorkspaceNotificationPoliciesResponse>(
    `/notification-policies/workspaces/${encodeURIComponent(workspaceId)}`,
    { method: 'GET' },
  )
}

export async function patchWorkspaceNotificationPolicies(
  workspaceId: string,
  updates: WorkspaceNotificationPolicyUpdate[],
): Promise<WorkspaceNotificationPoliciesResponse> {
  return apiRequest<WorkspaceNotificationPoliciesResponse>(
    `/notification-policies/workspaces/${encodeURIComponent(workspaceId)}`,
    { method: 'PATCH', body: JSON.stringify({ updates }) },
  )
}

export async function resetWorkspaceNotificationPolicies(
  workspaceId: string,
): Promise<WorkspaceNotificationPoliciesResponse> {
  return apiRequest<WorkspaceNotificationPoliciesResponse>(
    `/notification-policies/workspaces/${encodeURIComponent(workspaceId)}/reset`,
    { method: 'POST' },
  )
}

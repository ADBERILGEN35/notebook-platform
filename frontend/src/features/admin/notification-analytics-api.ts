import { apiRequest } from '../../shared/api/api-client'

export type NotificationAnalyticsSummary = {
  from: string
  to: string
  bucket: string
  totals: {
    created: number
    sent: number
    failed: number
    dead: number
    skippedPreference: number
    digestQueued: number
    digestSent: number
    quietHoursDelayed: number
  }
  byChannel: { channel: string; created: number; queued: number; sent: number; failed: number }[]
  byType: { notificationType: string; created: number }[]
  fanout: { pending: number; retrying: number; dead: number }
  sse: { activeConnections: number; sendFailuresInRange: number; eventsSentMeterTotal: number }
  redisFanout: { publishSuccessInRange: number; publishFailureInRange: number; subscriberReceivedInRange: number }
  digest: { pendingItems: number; workerEnabled: boolean; digestEnabled: boolean }
  workers: {
    fanoutWorkerLastRun: string | null
    digestWorkerLastRun: string | null
    emailWorkerLastRun: string | null
    fanoutWorkerEnabled: boolean
    emailWorkerEnabled: boolean
  }
}

export async function fetchNotificationAnalyticsSummary(params: {
  from: string
  to: string
  bucket?: string
}): Promise<NotificationAnalyticsSummary> {
  const q = new URLSearchParams({ from: params.from, to: params.to })
  if (params.bucket) q.set('bucket', params.bucket)
  return apiRequest<NotificationAnalyticsSummary>(`/admin/notifications/analytics/summary?${q.toString()}`, {
    method: 'GET',
  })
}

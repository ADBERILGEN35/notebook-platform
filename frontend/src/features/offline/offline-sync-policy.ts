import { ApiError } from '../../shared/api/api-client'
import type { OfflineDraftStatus } from './offline-sync-types'

export type SyncPolicyResult =
  | { outcome: 'success' }
  | { outcome: 'conflict'; reason: string }
  | { outcome: 'failed'; reason: string; requeue: boolean }
  | { outcome: 'not_found'; reason: string }

/**
 * Maps PATCH /notes sync outcomes to draft status updates (design contract; no I/O).
 * - 412 -> CONFLICT (Faz 66 manual resolution)
 * - 404 -> FAILED (note deleted)
 * - 403 -> FAILED (permission)
 * - 503 / network-style -> requeue (stay QUEUED)
 */
export function mapHttpErrorToSyncPolicy(err: unknown): SyncPolicyResult {
  if (!(err instanceof ApiError)) {
    return { outcome: 'failed', reason: 'NETWORK_OR_UNKNOWN', requeue: true }
  }
  if (err.status === 412 || err.status === 409) {
    return { outcome: 'conflict', reason: err.errorCode || 'NOTE_CONFLICT' }
  }
  if (err.status === 404) {
    return { outcome: 'not_found', reason: err.errorCode || 'NOTE_NOT_FOUND' }
  }
  if (err.status === 403 || err.status === 401) {
    return { outcome: 'failed', reason: err.errorCode || 'PERMISSION_DENIED', requeue: false }
  }
  if (err.status === 503 || err.status === 502 || err.status === 504) {
    return { outcome: 'failed', reason: err.errorCode || 'SERVICE_UNAVAILABLE', requeue: true }
  }
  return { outcome: 'failed', reason: err.errorCode || `HTTP_${err.status}`, requeue: false }
}

export function draftStatusAfterSyncPolicy(policy: SyncPolicyResult): OfflineDraftStatus {
  switch (policy.outcome) {
    case 'success':
      return 'SYNCED'
    case 'conflict':
      return 'CONFLICT'
    case 'not_found':
      return 'FAILED'
    case 'failed':
      return policy.requeue ? 'QUEUED' : 'FAILED'
    default:
      return 'FAILED'
  }
}

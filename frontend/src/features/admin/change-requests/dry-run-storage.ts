import type { GitOpsDryRunResponse } from '../enterprise/change-requests-api'

const prefix = 'admin-cr-dry-run-'

export function stashDryRunResult(changeRequestId: string, result: GitOpsDryRunResponse): void {
  try {
    sessionStorage.setItem(`${prefix}${changeRequestId}`, JSON.stringify(result))
  } catch {
    /* quota / private mode */
  }
}

export function readDryRunResult(changeRequestId: string): GitOpsDryRunResponse | null {
  try {
    const raw = sessionStorage.getItem(`${prefix}${changeRequestId}`)
    if (!raw) return null
    return JSON.parse(raw) as GitOpsDryRunResponse
  } catch {
    return null
  }
}

export function clearDryRunResult(changeRequestId: string): void {
  try {
    sessionStorage.removeItem(`${prefix}${changeRequestId}`)
  } catch {
    /* ignore */
  }
}

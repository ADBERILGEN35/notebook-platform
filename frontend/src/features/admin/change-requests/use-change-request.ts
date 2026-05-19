import { useCallback, useEffect, useState } from 'react'
import {
  listChangeRequests,
  type ChangeRequestItem,
  type ChangeRequestStatusFilter,
} from '../enterprise/change-requests-api'
import { extractApiErrorFields } from './change-request-utils'
import { readableErrorMessage } from '../../../shared/api/api-client'

export function useChangeRequestList(statusFilter: ChangeRequestStatusFilter) {
  const [items, setItems] = useState<ChangeRequestItem[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const r = await listChangeRequests(statusFilter)
      setItems(r.items)
    } catch (e: unknown) {
      const api = extractApiErrorFields(e)
      setError(api ? `${api.errorCode}: ${api.message}` : readableErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }, [statusFilter])

  useEffect(() => {
    void refresh()
  }, [refresh])

  return { items, loading, error, refresh }
}

export function useChangeRequestById(id: string | undefined) {
  const { items, loading, error, refresh } = useChangeRequestList('ALL')
  const row = items?.find((i) => i.id === id) ?? null
  return { row, loading, error, refresh, notFound: !loading && items !== null && !row && !!id }
}

import { useCallback, useEffect, useState } from 'react'
import { fetchDeadLetterList, type DeadLetterListItem } from '../notification-dead-letter-api'

export function useDeadLetterEvent(eventId: string | undefined) {
  const [item, setItem] = useState<DeadLetterListItem | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!eventId) {
      setItem(null)
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    try {
      const res = await fetchDeadLetterList({ page: 0, size: 100 })
      const found = res.items.find((r) => r.id === eventId) ?? null
      if (!found) {
        setError('Dead-letter event not found in the current list window.')
      }
      setItem(found)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load dead-letter event')
      setItem(null)
    } finally {
      setLoading(false)
    }
  }, [eventId])

  useEffect(() => {
    void load()
  }, [load])

  return { item, loading, error, reload: load }
}

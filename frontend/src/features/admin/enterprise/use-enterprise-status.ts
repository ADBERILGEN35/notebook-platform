import { useQuery } from '@tanstack/react-query'
import { fetchEnterpriseStatus } from './enterprise-api'

export function useEnterpriseStatus() {
  return useQuery({
    queryKey: ['admin-enterprise-status'],
    queryFn: fetchEnterpriseStatus,
    staleTime: 60_000,
  })
}

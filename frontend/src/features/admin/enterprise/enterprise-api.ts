import { apiRequest } from '../../../shared/api/api-client'
import { enterpriseStatusResponseSchema, type EnterpriseStatusResponse } from './enterprise-schema'

/**
 * Faz 63: Gateway admin-only aggregated enterprise status. Never calls internal service URLs from the browser.
 */
export async function fetchEnterpriseStatus(): Promise<EnterpriseStatusResponse> {
  const raw = await apiRequest<unknown>('/admin/enterprise/status', { method: 'GET' })
  const parsed = enterpriseStatusResponseSchema.safeParse(raw)
  if (!parsed.success) {
    throw new Error('Invalid enterprise status response')
  }
  return parsed.data
}

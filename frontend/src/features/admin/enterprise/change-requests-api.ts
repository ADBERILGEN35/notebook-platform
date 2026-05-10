import { apiRequest } from '../../../shared/api/api-client'

export type ChangeRequestStatusFilter = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'ALL'

export type ChangeRequestItem = {
  id: string
  requestedByUserId: string
  status: string
  operationType: string
  targetService: string
  targetKey: string
  currentValue: string | null
  requestedValue: string
  severity: string | null
  impactSummary: Record<string, unknown> | null
  validationResult: Record<string, unknown> | null
  createdAt: string
  externalRequestId: string | null
  decidedAt: string | null
  decidedByUserId: string | null
  decisionReason: string | null
  approvedAt: string | null
  rejectedAt: string | null
}

export type ChangeRequestListResponse = {
  items: ChangeRequestItem[]
}

export type ValidateChangeRequestBody = {
  operationType: string
  requestedValue: string
  currentValue?: string | null
}

export type ValidateChangeRequestResponse = {
  valid: boolean
  requiresApproval: boolean
  impactSummary: Record<string, unknown>
  validationResult: Record<string, unknown>
}

export type CreateChangeRequestBody = {
  operationType: string
  requestedValue: string
  currentValue?: string | null
  confirmation?: string | null
}

export type CreateChangeRequestResponse = {
  id: string
  status: string
  operationType: string
  createdAt: string
}

export type ApproveChangeRequestBody = {
  reason?: string | null
}

export type ApproveChangeRequestResponse = {
  id: string
  status: string
  decidedAt: string
  decidedByUserId: string
  operationType: string
  targetService: string
  targetKey: string
  requestedValue: string
  nextStep: Record<string, string>
}

export type RejectChangeRequestBody = {
  reason?: string | null
}

export type RejectChangeRequestResponse = {
  id: string
  status: string
  decisionReason: string | null
  decidedAt: string
}

export const ADMIN_CHANGE_REQUEST_OPERATIONS: {
  type: string
  label: string
  valueOptions: string[]
}[] = [
  {
    type: 'ADMIN_MFA_MODE_UPDATE',
    label: 'Gateway admin MFA mode (GitOps handoff)',
    valueOptions: ['off', 'observe', 'warn', 'enforce'],
  },
  {
    type: 'MERGE_ANALYSIS_ROLLOUT_REQUEST',
    label: 'Merge analysis rollout (content-service)',
    valueOptions: ['true', 'false'],
  },
  {
    type: 'MERGE_APPLY_ROLLOUT_REQUEST',
    label: 'Merge apply rollout (content-service)',
    valueOptions: ['true', 'false'],
  },
  {
    type: 'SCIM_BULK_ROLLOUT_REQUEST',
    label: 'SCIM bulk operations rollout (identity-service)',
    valueOptions: ['true', 'false'],
  },
]

export async function listChangeRequests(status?: ChangeRequestStatusFilter): Promise<ChangeRequestListResponse> {
  const q =
    status && status !== 'ALL' ? `?status=${encodeURIComponent(status)}` : ''
  return apiRequest<ChangeRequestListResponse>(`/admin/enterprise/change-requests${q}`, { method: 'GET' })
}

export async function validateChangeRequest(
  body: ValidateChangeRequestBody,
): Promise<ValidateChangeRequestResponse> {
  return apiRequest<ValidateChangeRequestResponse>('/admin/enterprise/change-requests/validate', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function createChangeRequest(body: CreateChangeRequestBody): Promise<CreateChangeRequestResponse> {
  return apiRequest<CreateChangeRequestResponse>('/admin/enterprise/change-requests', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function cancelChangeRequest(id: string): Promise<void> {
  await apiRequest<null>(`/admin/enterprise/change-requests/${encodeURIComponent(id)}/cancel`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
}

export async function approveChangeRequest(
  id: string,
  body: ApproveChangeRequestBody,
): Promise<ApproveChangeRequestResponse> {
  return apiRequest<ApproveChangeRequestResponse>(
    `/admin/enterprise/change-requests/${encodeURIComponent(id)}/approve`,
    {
      method: 'POST',
      body: JSON.stringify(body.reason != null ? { reason: body.reason } : {}),
    },
  )
}

export async function rejectChangeRequest(
  id: string,
  body: RejectChangeRequestBody,
): Promise<RejectChangeRequestResponse> {
  return apiRequest<RejectChangeRequestResponse>(
    `/admin/enterprise/change-requests/${encodeURIComponent(id)}/reject`,
    {
      method: 'POST',
      body: JSON.stringify(body.reason != null ? { reason: body.reason } : {}),
    },
  )
}

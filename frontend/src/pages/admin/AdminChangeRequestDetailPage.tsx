import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import { Modal } from '../../shared/components/Modal'
import { ApiError, readableErrorMessage } from '../../shared/api/api-client'
import {
  isEnterpriseAdminApprovalsUiEnabled,
  isEnterpriseAdminWriteEnabled,
  isEnterpriseGitOpsPrUiEnabled,
  isEnterpriseGitOpsRbacRoleRequestsUiEnabled,
} from '../../shared/config/admin-feature-flags'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_CHANGE_REQUEST_APPROVE,
  PERM_CHANGE_REQUEST_CANCEL,
  PERM_CHANGE_REQUEST_CREATE,
  PERM_CHANGE_REQUEST_REJECT,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import * as changeRequestsApi from '../../features/admin/enterprise/change-requests-api'
import { ApprovalGatePanel } from '../../features/admin/change-requests/ApprovalGatePanel'
import { ChangeRequestTimeline } from '../../features/admin/change-requests/ChangeRequestTimeline'
import {
  ChangeRequestStatusBadge,
  OperationTypeBadge,
  SeverityBadge,
} from '../../features/admin/change-requests/ChangeRequestBadges'
import { GitOpsDisabledBanner } from '../../features/admin/change-requests/GitOpsDisabledBanner'
import { useChangeRequestById } from '../../features/admin/change-requests/use-change-request'
import {
  gitOpsAvailableForRow,
  isHighSeverity,
  isRbacRoleRequest,
  maskUserId,
  rowSeverity,
  sanitizeStructuredPreview,
} from '../../features/admin/change-requests/change-request-utils'
import { RbacOverrideDiffPanel } from '../../features/admin/change-requests/RbacOverrideDiffPanel'
import { readDryRunResult } from '../../features/admin/change-requests/dry-run-storage'

export function AdminChangeRequestDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { row, loading, error, refresh, notFound } = useChangeRequestById(id)
  const user = useAuthStore((s) => s.user)
  const approvalsUi = isEnterpriseAdminApprovalsUiEnabled()
  const gitOpsFlags = {
    gitOpsUi: isEnterpriseGitOpsPrUiEnabled(),
    gitOpsRbacUi: isEnterpriseGitOpsRbacRoleRequestsUiEnabled(),
  }

  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [approveOpen, setApproveOpen] = useState(false)
  const [rejectOpen, setRejectOpen] = useState(false)
  const [approveReason, setApproveReason] = useState('')
  const [approveConfirm, setApproveConfirm] = useState('')
  const [rejectReason, setRejectReason] = useState('')

  if (!isEnterpriseAdminWriteEnabled()) {
    return (
      <div className="space-y-4">
        <PageHeader title="Change request" />
        <GitOpsDisabledBanner reason="write" />
      </div>
    )
  }

  if (loading) return <LoadingState label="Loading change request" />
  if (error) return <ErrorAlert message={error} />
  if (notFound || !row) {
    return (
      <EmptyStateWrapper id={id} />
    )
  }

  const selfCreated = user?.id === row.requestedByUserId
  const canApprove = approvalsUi && hasPlatformPermission(user, PERM_CHANGE_REQUEST_APPROVE)
  const canReject = approvalsUi && hasPlatformPermission(user, PERM_CHANGE_REQUEST_REJECT)
  const canCancel =
    row.status === 'PENDING' &&
    (hasPlatformPermission(user, PERM_CHANGE_REQUEST_CANCEL) ||
      (selfCreated && hasPlatformPermission(user, PERM_CHANGE_REQUEST_CREATE)))
  const gitOk = gitOpsAvailableForRow(row, gitOpsFlags)
  const dryRun = readDryRunResult(row.id)
  const safeValidation = sanitizeStructuredPreview(row.validationResult as Record<string, unknown> | null)
  const safeImpact = sanitizeStructuredPreview(row.impactSummary)

  const submitApprove = async () => {
    if (isHighSeverity(row) && approveConfirm.trim() !== 'CONFIRM') return
    setBusy(true)
    setActionError(null)
    try {
      await changeRequestsApi.approveChangeRequest(row.id, { reason: approveReason.trim() || undefined })
      setApproveOpen(false)
      await refresh()
    } catch (e) {
      setActionError(e instanceof ApiError ? `${e.errorCode}: ${e.message}` : readableErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const submitReject = async () => {
    if (isHighSeverity(row) && !rejectReason.trim()) {
      setActionError('Rejection reason required for HIGH severity.')
      return
    }
    setBusy(true)
    try {
      await changeRequestsApi.rejectChangeRequest(row.id, { reason: rejectReason.trim() || undefined })
      setRejectOpen(false)
      await refresh()
    } catch (e) {
      setActionError(e instanceof ApiError ? `${e.errorCode}: ${e.message}` : readableErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-6" data-testid="change-request-detail-page">
      <PageHeader
        title="Change request detail"
        subtitle={`${row.operationType} Ã‚Â· ${row.targetEnvironment ?? 'staging'}`}
        actions={
          <Link to="/app/admin/change-requests" className="text-sm text-primary-700 underline">
            Back to list
          </Link>
        }
      />

      {actionError ? <ErrorAlert message={actionError} /> : null}

      <Card className="space-y-3 text-sm">
        <div className="flex flex-wrap gap-2">
          <ChangeRequestStatusBadge status={row.status} />
          <OperationTypeBadge operationType={row.operationType} />
          <SeverityBadge severity={rowSeverity(row)} />
        </div>
        <dl className="grid gap-2 sm:grid-cols-2 text-xs">
          <div>
            <dt className="text-slate-500">Id</dt>
            <dd className="font-mono">{row.id}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Requester</dt>
            <dd className="font-mono">{maskUserId(row.requestedByUserId)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Approver</dt>
            <dd className="font-mono">{maskUserId(row.decidedByUserId)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Target</dt>
            <dd>
              {row.targetService}:{row.targetKey}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Requested value</dt>
            <dd className="font-mono">{row.requestedValue}</dd>
          </div>
        </dl>
      </Card>

      <ApprovalGatePanel row={row} />
      <ChangeRequestTimeline row={row} />

      <Card className="space-y-2 text-xs">
        <p className="font-semibold text-slate-900">Impact summary</p>
        <pre className="max-h-40 overflow-auto rounded bg-slate-50 p-2">{JSON.stringify(safeImpact, null, 2)}</pre>
        <p className="font-semibold text-slate-900">Structured payload (sanitized)</p>
        <pre className="max-h-40 overflow-auto rounded bg-slate-50 p-2">{JSON.stringify(safeValidation, null, 2)}</pre>
        <p className="text-slate-500">Audit trail: decisions recorded server-side; no tokens in UI.</p>
      </Card>

      {isRbacRoleRequest(row) ? <RbacOverrideDiffPanel row={row} dryRun={dryRun} /> : null}

      <div className="flex flex-wrap gap-2">
        {row.status === 'PENDING' && canApprove ? (
          <button
            type="button"
            className="rounded bg-emerald-700 px-3 py-1.5 text-sm text-white disabled:opacity-50"
            disabled={busy || selfCreated}
            title={selfCreated ? 'Another admin must approve' : undefined}
            onClick={() => setApproveOpen(true)}
          >
            Approve
          </button>
        ) : null}
        {row.status === 'PENDING' && canReject ? (
          <button
            type="button"
            className="rounded bg-amber-800 px-3 py-1.5 text-sm text-white disabled:opacity-50"
            disabled={busy}
            onClick={() => setRejectOpen(true)}
          >
            Reject
          </button>
        ) : null}
        {canCancel ? (
          <button
            type="button"
            className="rounded border border-red-300 px-3 py-1.5 text-sm text-red-800"
            disabled={busy}
            onClick={async () => {
              setBusy(true)
              try {
                await changeRequestsApi.cancelChangeRequest(row.id)
                await refresh()
              } finally {
                setBusy(false)
              }
            }}
          >
            Cancel
          </button>
        ) : null}
        {gitOk ? (
          <button
            type="button"
            className="rounded bg-primary-600 px-3 py-1.5 text-sm text-white"
            onClick={() => navigate(`/app/admin/change-requests/${row.id}/gitops`)}
          >
            GitOps handoff
          </button>
        ) : null}
      </div>

      <Modal open={approveOpen} title="Approve change request" onClose={() => !busy && setApproveOpen(false)}>
        <div className="space-y-3 text-xs">
          <p className="text-amber-950">No runtime mutation Ã¢â‚¬â€ GitOps/runbook apply only.</p>
          {isHighSeverity(row) ? (
            <label className="block">
              Type CONFIRM
              <input className="mt-1 w-full rounded border px-2 py-1 font-mono" value={approveConfirm} onChange={(e) => setApproveConfirm(e.target.value)} />
            </label>
          ) : null}
          <label className="block">
            Note
            <textarea className="mt-1 w-full rounded border px-2 py-1" rows={2} value={approveReason} onChange={(e) => setApproveReason(e.target.value)} />
          </label>
          <button type="button" className="rounded bg-emerald-700 px-3 py-1.5 text-white" disabled={busy} onClick={() => void submitApprove()}>
            Confirm approve
          </button>
        </div>
      </Modal>

      <Modal open={rejectOpen} title="Reject change request" onClose={() => !busy && setRejectOpen(false)}>
        <div className="space-y-3 text-xs">
          <textarea className="w-full rounded border px-2 py-1" rows={3} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} />
          <button type="button" className="rounded bg-amber-800 px-3 py-1.5 text-white" disabled={busy} onClick={() => void submitReject()}>
            Confirm reject
          </button>
        </div>
      </Modal>
    </div>
  )
}

function EmptyStateWrapper({ id }: { id?: string }) {
  return (
    <div className="space-y-4">
      <PageHeader title="Change request not found" subtitle={id ? `No request with id ${id}` : undefined} />
      <Link to="/app/admin/change-requests" className="text-sm text-primary-700 underline">
        Back to list
      </Link>
    </div>
  )
}

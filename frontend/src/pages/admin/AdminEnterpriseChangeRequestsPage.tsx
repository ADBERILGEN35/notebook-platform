import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import { Modal } from '../../shared/components/Modal'
import { ApiError, readableErrorMessage } from '../../shared/api/api-client'
import {
  isEnterpriseAdminApprovalsUiEnabled,
  isEnterpriseAdminWriteEnabled,
} from '../../shared/config/admin-feature-flags'
import { useAuthStore } from '../../features/auth/auth-store'
import * as changeRequestsApi from '../../features/admin/enterprise/change-requests-api'
import type {
  ApproveChangeRequestResponse,
  ChangeRequestItem,
  ChangeRequestStatusFilter,
  ValidateChangeRequestResponse,
} from '../../features/admin/enterprise/change-requests-api'

const STATUS_FILTERS: { value: ChangeRequestStatusFilter; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'ALL', label: 'All' },
]

const APPROVAL_WORKFLOW_DOC = 'docs/admin-change-request-approval-workflow.md'

function rowSeverity(row: ChangeRequestItem): string {
  const fromRow = row.severity
  if (fromRow && String(fromRow).trim()) return String(fromRow)
  const im = row.impactSummary?.severity
  return typeof im === 'string' ? im : ''
}

function isHighSeverity(row: ChangeRequestItem): boolean {
  return rowSeverity(row).toUpperCase() === 'HIGH'
}

/** Works for ApiError, Error with assigned errorCode, and plain gateway-shaped objects. */
function extractApiErrorFields(e: unknown): { errorCode: string; message: string } | null {
  if (typeof e === 'object' && e !== null) {
    const rec = e as Record<string, unknown>
    const code = rec.errorCode
    if (typeof code === 'string') {
      const msg = typeof rec.message === 'string' ? rec.message : 'Request failed'
      return { errorCode: code, message: msg }
    }
  }
  return null
}

function NavigateToEnterpriseOverview() {
  return (
    <Card className="space-y-2">
      <p className="text-sm font-medium text-slate-900">Change requests UI is disabled</p>
      <p className="text-xs text-slate-600">
        Enable <code className="rounded bg-slate-100 px-1">FRONTEND_ENTERPRISE_ADMIN_WRITE_ENABLED</code> (and matching
        gateway / identity flags) to use this page.
      </p>
      <Link to="/app/admin/enterprise" className="text-sm text-primary-700 underline">
        Back to enterprise overview
      </Link>
    </Card>
  )
}

export function AdminEnterpriseChangeRequestsPage() {
  const [searchParams] = useSearchParams()
  const preOp = searchParams.get('op') ?? ''
  const preVal = searchParams.get('val') ?? ''
  const currentUser = useAuthStore((s) => s.user)
  const approvalsUi = isEnterpriseAdminApprovalsUiEnabled()

  const [items, setItems] = useState<ChangeRequestItem[] | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  const [loadingList, setLoadingList] = useState(true)
  const [statusFilter, setStatusFilter] = useState<ChangeRequestStatusFilter>('PENDING')

  const [operationType, setOperationType] = useState(
    () =>
      changeRequestsApi.ADMIN_CHANGE_REQUEST_OPERATIONS.find((o) => o.type === preOp)?.type ??
      changeRequestsApi.ADMIN_CHANGE_REQUEST_OPERATIONS[0].type,
  )
  const opMeta = useMemo(
    () => changeRequestsApi.ADMIN_CHANGE_REQUEST_OPERATIONS.find((o) => o.type === operationType)!,
    [operationType],
  )
  const [requestedValue, setRequestedValue] = useState(() => {
    const meta = changeRequestsApi.ADMIN_CHANGE_REQUEST_OPERATIONS.find(
      (o) => o.type === (preOp || changeRequestsApi.ADMIN_CHANGE_REQUEST_OPERATIONS[0].type),
    )!
    if (preVal && meta.valueOptions.includes(preVal)) return preVal
    return meta.valueOptions[0]
  })
  const [confirmation, setConfirmation] = useState('')

  const [validateResult, setValidateResult] = useState<ValidateChangeRequestResponse | null>(null)
  const [validateOk, setValidateOk] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const [approveTarget, setApproveTarget] = useState<ChangeRequestItem | null>(null)
  const [approveReason, setApproveReason] = useState('')
  const [approveConfirm, setApproveConfirm] = useState('')
  const [rejectTarget, setRejectTarget] = useState<ChangeRequestItem | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [detailRow, setDetailRow] = useState<ChangeRequestItem | null>(null)
  const [approveFlash, setApproveFlash] = useState<ApproveChangeRequestResponse | null>(null)

  const refresh = useCallback(async () => {
    setLoadingList(true)
    setListError(null)
    try {
      const r = await changeRequestsApi.listChangeRequests(statusFilter)
      setItems(r.items)
    } catch (e: unknown) {
      const api = extractApiErrorFields(e)
      if (api) {
        if (api.errorCode === 'ADMIN_ACCESS_DENIED') {
          setListError('Permission denied — platform admin access required.')
        } else {
          setListError(`${api.errorCode}: ${api.message}`)
        }
      } else {
        setListError(readableErrorMessage(e))
      }
    } finally {
      setLoadingList(false)
    }
  }, [statusFilter])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    setRequestedValue((prev) => (opMeta.valueOptions.includes(prev) ? prev : opMeta.valueOptions[0]))
    setValidateResult(null)
    setValidateOk(false)
    setConfirmation('')
  }, [operationType, opMeta])

  if (!isEnterpriseAdminWriteEnabled()) {
    return <NavigateToEnterpriseOverview />
  }

  const severity = validateResult?.impactSummary?.severity
  const needsConfirm = typeof severity === 'string' && severity.toUpperCase() === 'HIGH'
  const canCreate = validateOk && (!needsConfirm || confirmation.trim() === 'CONFIRM')

  const onValidate = async () => {
    setActionError(null)
    setBusy(true)
    setValidateOk(false)
    try {
      const r = await changeRequestsApi.validateChangeRequest({ operationType, requestedValue })
      setValidateResult(r)
      setValidateOk(!!r.valid)
    } catch (e) {
      setValidateResult(null)
      setValidateOk(false)
      setActionError(e instanceof ApiError ? `${e.errorCode}: ${e.message}` : readableErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const onCreate = async () => {
    setActionError(null)
    setBusy(true)
    try {
      await changeRequestsApi.createChangeRequest({
        operationType,
        requestedValue,
        confirmation: needsConfirm ? confirmation.trim() : undefined,
      })
      setValidateResult(null)
      setValidateOk(false)
      setConfirmation('')
      await refresh()
    } catch (e) {
      setActionError(e instanceof ApiError ? `${e.errorCode}: ${e.message}` : readableErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const onCancel = async (id: string) => {
    setActionError(null)
    setBusy(true)
    try {
      await changeRequestsApi.cancelChangeRequest(id)
      await refresh()
    } catch (e) {
      setActionError(e instanceof ApiError ? `${e.errorCode}: ${e.message}` : readableErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const openApprove = (row: ChangeRequestItem) => {
    setApproveFlash(null)
    setApproveReason('')
    setApproveConfirm('')
    setApproveTarget(row)
  }

  const submitApprove = async () => {
    if (!approveTarget) return
    const high = isHighSeverity(approveTarget)
    if (high && approveConfirm.trim() !== 'CONFIRM') return
    setActionError(null)
    setBusy(true)
    try {
      const r = await changeRequestsApi.approveChangeRequest(approveTarget.id, {
        reason: approveReason.trim() || undefined,
      })
      setApproveTarget(null)
      setApproveFlash(r)
      await refresh()
    } catch (e) {
      setActionError(e instanceof ApiError ? `${e.errorCode}: ${e.message}` : readableErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const submitReject = async () => {
    if (!rejectTarget) return
    const high = isHighSeverity(rejectTarget)
    if (high && !rejectReason.trim()) {
      setActionError('Rejection reason is required for high-severity requests.')
      return
    }
    setActionError(null)
    setBusy(true)
    try {
      await changeRequestsApi.rejectChangeRequest(rejectTarget.id, { reason: rejectReason.trim() || undefined })
      setRejectTarget(null)
      setRejectReason('')
      await refresh()
    } catch (e) {
      setActionError(e instanceof ApiError ? `${e.errorCode}: ${e.message}` : readableErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const openReject = (row: ChangeRequestItem) => {
    setRejectReason('')
    setRejectTarget(row)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Enterprise change requests"
        subtitle="Validate impact, open a pending request, then another platform admin can approve or reject. Runtime config is not mutated automatically — apply via GitOps or runbooks."
      />

      <p className="text-xs text-slate-600">
        <Link to="/app/admin/enterprise" className="text-primary-700 underline">
          Back to enterprise overview
        </Link>
        {' · '}
        <span className="text-slate-500">
          Runbook reference: <code className="rounded bg-slate-100 px-1">{APPROVAL_WORKFLOW_DOC}</code>
        </span>
      </p>

      {actionError ? <ErrorAlert message={actionError} /> : null}

      {approveFlash ? (
        <Card className="border-emerald-200 bg-emerald-50/80 text-sm text-emerald-950">
          <p className="font-semibold">Request approved</p>
          <p className="mt-1 text-xs">
            {approveFlash.nextStep?.message ?? 'Apply is not automatic. Follow GitOps / the platform runbook.'}
          </p>
          <p className="mt-2 text-xs text-emerald-900">
            See <code className="rounded bg-white/80 px-1">{APPROVAL_WORKFLOW_DOC}</code> for the full approval workflow
            and handoff steps.
          </p>
        </Card>
      ) : null}

      {!approvalsUi ? (
        <p className="text-xs text-amber-800">
          Approvals UI is turned off (<code className="rounded bg-amber-100 px-1">ENTERPRISE_ADMIN_APPROVALS_ENABLED</code>
          ). You can still create and cancel pending requests; approve/reject from the console is hidden.
        </p>
      ) : null}

      <Card className="space-y-3">
        <p className="text-xs font-semibold uppercase text-slate-500">Create request</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-xs text-slate-600">
            Operation
            <select
              className="mt-1 w-full rounded border border-slate-200 px-2 py-1.5 text-sm"
              value={operationType}
              onChange={(e) => setOperationType(e.target.value)}
              disabled={busy}
            >
              {changeRequestsApi.ADMIN_CHANGE_REQUEST_OPERATIONS.map((o) => (
                <option key={o.type} value={o.type}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-xs text-slate-600">
            Requested value
            <select
              className="mt-1 w-full rounded border border-slate-200 px-2 py-1.5 text-sm"
              value={requestedValue}
              onChange={(e) => setRequestedValue(e.target.value)}
              disabled={busy}
            >
              {opMeta.valueOptions.map((v) => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="rounded bg-slate-800 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            onClick={() => void onValidate()}
            disabled={busy}
          >
            Validate
          </button>
          <button
            type="button"
            className="rounded bg-primary-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            onClick={() => void onCreate()}
            disabled={busy || !canCreate}
          >
            Create pending request
          </button>
        </div>
        {validateResult ? (
          <div className="rounded border border-slate-200 bg-slate-50 p-3 text-xs text-slate-800">
            <p className="font-semibold text-slate-900">Impact preview</p>
            <ul className="mt-2 list-inside list-disc space-y-1">
              <li>Severity: {String(validateResult.impactSummary.severity ?? '—')}</li>
              <li>{String(validateResult.impactSummary.description ?? '')}</li>
              <li>Rollback: {String(validateResult.impactSummary.rollback ?? '—')}</li>
              <li>Runtime apply supported: {String(validateResult.impactSummary.runtimeApplySupported ?? false)}</li>
            </ul>
            {needsConfirm ? (
              <label className="mt-3 block text-slate-700">
                Type CONFIRM for high-severity requests
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-2 py-1 font-mono text-sm"
                  value={confirmation}
                  onChange={(e) => setConfirmation(e.target.value)}
                  autoComplete="off"
                  disabled={busy}
                />
              </label>
            ) : null}
          </div>
        ) : null}
      </Card>

      <Card className="space-y-3">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-xs font-semibold uppercase text-slate-500">Requests</p>
          <div className="flex flex-wrap gap-1">
            {STATUS_FILTERS.map((f) => (
              <button
                key={f.value}
                type="button"
                className={`rounded px-2 py-1 text-xs font-medium ${
                  statusFilter === f.value
                    ? 'bg-slate-800 text-white'
                    : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                }`}
                onClick={() => setStatusFilter(f.value)}
                disabled={busy}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {loadingList ? <LoadingState label="Loading change requests" /> : null}
        {listError ? <ErrorAlert message={listError} /> : null}
        {!loadingList && items && items.length === 0 ? (
          <p className="text-sm text-slate-600">No requests for this filter.</p>
        ) : null}
        {!loadingList && items && items.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-xs text-slate-800">
              <thead>
                <tr className="border-b border-slate-200 text-slate-500">
                  <th className="py-2 pr-2">Created</th>
                  <th className="py-2 pr-2">Status</th>
                  <th className="py-2 pr-2">Operation</th>
                  <th className="py-2 pr-2">Target</th>
                  <th className="py-2 pr-2">Value</th>
                  <th className="py-2 pr-2">Severity</th>
                  <th className="py-2 pr-2" />
                </tr>
              </thead>
              <tbody>
                {items.map((row) => {
                  const selfCreated = currentUser?.id === row.requestedByUserId
                  const canActPending = row.status === 'PENDING' && approvalsUi
                  const selfApproveBlocked = canActPending && selfCreated
                  return (
                    <tr key={row.id} className="border-b border-slate-100">
                      <td className="py-2 pr-2 whitespace-nowrap">{new Date(row.createdAt).toLocaleString()}</td>
                      <td className="py-2 pr-2 font-medium">{row.status}</td>
                      <td className="py-2 pr-2 font-mono">{row.operationType}</td>
                      <td className="py-2 pr-2">
                        {row.targetService}:{row.targetKey}
                      </td>
                      <td className="py-2 pr-2 font-mono">{row.requestedValue}</td>
                      <td className="py-2 pr-2">{rowSeverity(row) || '—'}</td>
                      <td className="py-2 pr-2 space-x-2 whitespace-nowrap">
                        <button
                          type="button"
                          className="text-primary-700 underline"
                          disabled={busy}
                          onClick={() => setDetailRow(row)}
                        >
                          Details
                        </button>
                        {canActPending ? (
                          <>
                            <button
                              type="button"
                              className="text-emerald-800 underline disabled:opacity-50"
                              disabled={busy || !!selfApproveBlocked}
                              title={
                                selfApproveBlocked
                                  ? 'Another platform admin must approve this request.'
                                  : undefined
                              }
                              onClick={() => openApprove(row)}
                            >
                              Approve
                            </button>
                            <button
                              type="button"
                              className="text-amber-800 underline disabled:opacity-50"
                              disabled={busy}
                              onClick={() => openReject(row)}
                            >
                              Reject
                            </button>
                          </>
                        ) : null}
                        {row.status === 'PENDING' && selfCreated ? (
                          <button
                            type="button"
                            className="text-red-700 underline disabled:opacity-50"
                            disabled={busy}
                            onClick={() => void onCancel(row.id)}
                          >
                            Cancel
                          </button>
                        ) : null}
                        {row.status === 'APPROVED' ? (
                          <span className="text-emerald-800">GitOps handoff</span>
                        ) : null}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        ) : null}
      </Card>

      <Modal
        open={!!approveTarget}
        title="Approve change request"
        onClose={() => {
          if (!busy) setApproveTarget(null)
        }}
      >
        {approveTarget ? (
          <div className="space-y-3 text-xs text-slate-800">
            <p className="rounded border border-amber-200 bg-amber-50 p-2 text-amber-950">
              This does not apply the change automatically. Record the approval, then update configuration via GitOps or
              your runbook.
            </p>
            <div>
              <p className="font-semibold text-slate-900">Impact summary</p>
              <ul className="mt-1 list-inside list-disc space-y-1">
                <li>Severity: {rowSeverity(approveTarget) || '—'}</li>
                <li>{String(approveTarget.impactSummary?.description ?? '')}</li>
                <li>Rollback: {String(approveTarget.impactSummary?.rollback ?? '—')}</li>
              </ul>
            </div>
            <label className="block">
              Optional note for auditors
              <textarea
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
                rows={2}
                value={approveReason}
                onChange={(e) => setApproveReason(e.target.value)}
                disabled={busy}
              />
            </label>
            {isHighSeverity(approveTarget) ? (
              <label className="block">
                Type CONFIRM to approve a high-severity request
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-2 py-1 font-mono text-sm"
                  value={approveConfirm}
                  onChange={(e) => setApproveConfirm(e.target.value)}
                  autoComplete="off"
                  disabled={busy}
                />
              </label>
            ) : null}
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                className="rounded border border-slate-300 px-3 py-1.5 text-sm"
                disabled={busy}
                onClick={() => setApproveTarget(null)}
              >
                Close
              </button>
              <button
                type="button"
                className="rounded bg-emerald-700 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                disabled={
                  busy ||
                  (isHighSeverity(approveTarget) && approveConfirm.trim() !== 'CONFIRM')
                }
                onClick={() => void submitApprove()}
              >
                Approve
              </button>
            </div>
          </div>
        ) : null}
      </Modal>

      <Modal
        open={!!rejectTarget}
        title="Reject change request"
        onClose={() => {
          if (!busy) setRejectTarget(null)
        }}
      >
        {rejectTarget ? (
          <div className="space-y-3 text-xs text-slate-800">
            <p>
              Operation <span className="font-mono">{rejectTarget.operationType}</span> — requested value{' '}
              <span className="font-mono">{rejectTarget.requestedValue}</span>
            </p>
            {isHighSeverity(rejectTarget) ? (
              <p className="text-amber-900">A rejection reason is required for high-severity requests.</p>
            ) : (
              <p className="text-slate-600">A reason helps operators and auditors understand the decision.</p>
            )}
            <label className="block">
              Reason
              <textarea
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
                rows={3}
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                disabled={busy}
                required={isHighSeverity(rejectTarget)}
              />
            </label>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                className="rounded border border-slate-300 px-3 py-1.5 text-sm"
                disabled={busy}
                onClick={() => setRejectTarget(null)}
              >
                Close
              </button>
              <button
                type="button"
                className="rounded bg-amber-800 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                disabled={busy || (isHighSeverity(rejectTarget) && !rejectReason.trim())}
                onClick={() => void submitReject()}
              >
                Reject
              </button>
            </div>
          </div>
        ) : null}
      </Modal>

      {detailRow ? (
        <div className="fixed inset-0 z-30 grid place-items-center bg-slate-900/40 p-4">
          <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-lg border border-slate-200 bg-white p-4 text-xs text-slate-800 shadow-lg">
            <div className="mb-3 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-900">Change request details</h3>
              <button type="button" className="text-sm text-slate-500" onClick={() => setDetailRow(null)}>
                Close
              </button>
            </div>
            <dl className="space-y-2">
              <div>
                <dt className="text-slate-500">Id</dt>
                <dd className="font-mono text-slate-900">{detailRow.id}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Status</dt>
                <dd>{detailRow.status}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Requested by</dt>
                <dd className="font-mono">{detailRow.requestedByUserId}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Operation</dt>
                <dd className="font-mono">{detailRow.operationType}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Target</dt>
                <dd>
                  {detailRow.targetService}:{detailRow.targetKey}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500">Requested value</dt>
                <dd className="font-mono">{detailRow.requestedValue}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Severity</dt>
                <dd>{rowSeverity(detailRow) || '—'}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Impact summary</dt>
                <dd className="whitespace-pre-wrap rounded bg-slate-50 p-2">
                  {JSON.stringify(detailRow.impactSummary ?? {}, null, 2)}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500">Validation snapshot</dt>
                <dd className="whitespace-pre-wrap rounded bg-slate-50 p-2">
                  {JSON.stringify(detailRow.validationResult ?? {}, null, 2)}
                </dd>
              </div>
              {detailRow.decisionReason ? (
                <div>
                  <dt className="text-slate-500">Decision reason</dt>
                  <dd className="whitespace-pre-wrap">{detailRow.decisionReason}</dd>
                </div>
              ) : null}
              <div>
                <dt className="text-slate-500">Timestamps</dt>
                <dd>
                  Created {new Date(detailRow.createdAt).toLocaleString()}
                  {detailRow.decidedAt ? ` · Decided ${new Date(detailRow.decidedAt).toLocaleString()}` : ''}
                </dd>
              </div>
              <div>
                <dt className="text-slate-500">Next step</dt>
                <dd>
                  {detailRow.status === 'APPROVED'
                    ? 'Apply via GitOps or runbook — see docs/admin-change-request-approval-workflow.md'
                    : detailRow.status === 'PENDING'
                      ? 'Await approval from a different platform admin (four-eyes).'
                      : 'No further action in-console for this status.'}
                </dd>
              </div>
            </dl>
          </div>
        </div>
      ) : null}
    </div>
  )
}

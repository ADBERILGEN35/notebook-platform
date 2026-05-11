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
  isEnterpriseGitOpsPrUiEnabled,
  isEnterpriseGitOpsRbacRoleRequestsUiEnabled,
} from '../../shared/config/admin-feature-flags'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_CHANGE_REQUEST_APPROVE,
  PERM_CHANGE_REQUEST_CANCEL,
  PERM_CHANGE_REQUEST_CREATE,
  PERM_CHANGE_REQUEST_GITOPS_CREATE,
  PERM_CHANGE_REQUEST_GITOPS_DRY_RUN,
  PERM_CHANGE_REQUEST_REJECT,
  canCreateChangeRequestForOperation,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import * as changeRequestsApi from '../../features/admin/enterprise/change-requests-api'
import type {
  ApproveChangeRequestResponse,
  ChangeRequestItem,
  ChangeRequestStatusFilter,
  GitOpsDryRunResponse,
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
  const preCr = searchParams.get('cr') ?? ''
  const currentUser = useAuthStore((s) => s.user)
  const approvalsUi = isEnterpriseAdminApprovalsUiEnabled()
  const gitOpsUi = isEnterpriseGitOpsPrUiEnabled()
  const gitOpsRbacUi = isEnterpriseGitOpsRbacRoleRequestsUiEnabled()
  const canCreateFlow = hasPlatformPermission(currentUser, PERM_CHANGE_REQUEST_CREATE)
  const canApprove = hasPlatformPermission(currentUser, PERM_CHANGE_REQUEST_APPROVE)
  const canReject = hasPlatformPermission(currentUser, PERM_CHANGE_REQUEST_REJECT)
  const canCancelScoped = hasPlatformPermission(currentUser, PERM_CHANGE_REQUEST_CANCEL)
  const canCancelSelf = hasPlatformPermission(currentUser, PERM_CHANGE_REQUEST_CREATE)
  const canGitOpsDryRun = hasPlatformPermission(currentUser, PERM_CHANGE_REQUEST_GITOPS_DRY_RUN)
  const canGitOpsCreate = hasPlatformPermission(currentUser, PERM_CHANGE_REQUEST_GITOPS_CREATE)

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
  const canOperateType = useMemo(
    () => canCreateChangeRequestForOperation(currentUser, operationType),
    [currentUser, operationType],
  )
  const [requestedValue, setRequestedValue] = useState(() => {
    const meta = changeRequestsApi.ADMIN_CHANGE_REQUEST_OPERATIONS.find(
      (o) => o.type === (preOp || changeRequestsApi.ADMIN_CHANGE_REQUEST_OPERATIONS[0].type),
    )!
    if (preVal && meta.valueOptions.includes(preVal)) return preVal
    return meta.valueOptions[0]
  })
  const [confirmation, setConfirmation] = useState('')
  const [createTargetEnv, setCreateTargetEnv] = useState('staging')

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

  const [gitOpsDryRunRow, setGitOpsDryRunRow] = useState<ChangeRequestItem | null>(null)
  const [gitOpsDryRunEnv, setGitOpsDryRunEnv] = useState('staging')
  const [gitOpsDryRunResult, setGitOpsDryRunResult] = useState<GitOpsDryRunResponse | null>(null)
  const [gitOpsCreateRow, setGitOpsCreateRow] = useState<ChangeRequestItem | null>(null)
  const [gitOpsCreateConfirm, setGitOpsCreateConfirm] = useState('')
  const [gitOpsBusy, setGitOpsBusy] = useState(false)
  const [gitOpsError, setGitOpsError] = useState<string | null>(null)
  const [gitOpsCreateFlash, setGitOpsCreateFlash] = useState<{ url: string; status: string } | null>(null)

  const refresh = useCallback(async () => {
    setLoadingList(true)
    setListError(null)
    try {
      const r = await changeRequestsApi.listChangeRequests(statusFilter)
      setItems(r.items)
    } catch (e: unknown) {
      const api = extractApiErrorFields(e)
      if (api) {
        if (api.errorCode === 'ADMIN_ACCESS_DENIED' || api.errorCode === 'ADMIN_PERMISSION_REQUIRED') {
          setListError('Permission denied — required admin permission is missing.')
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
    if (!preCr.trim() || !items?.length) return
    const row = items.find((i) => i.id === preCr.trim())
    if (row) setDetailRow(row)
  }, [preCr, items])

  useEffect(() => {
    setRequestedValue((prev) => (opMeta.valueOptions.includes(prev) ? prev : opMeta.valueOptions[0]))
    setValidateResult(null)
    setValidateOk(false)
    setConfirmation('')
  }, [operationType, opMeta])

  useEffect(() => {
    if (gitOpsDryRunRow) {
      const e = gitOpsDryRunRow.targetEnvironment?.trim() || 'staging'
      setGitOpsDryRunEnv(e)
      setGitOpsDryRunResult(null)
      setGitOpsError(null)
    }
  }, [gitOpsDryRunRow])

  useEffect(() => {
    if (gitOpsCreateRow) {
      setGitOpsCreateConfirm('')
      setGitOpsError(null)
    }
  }, [gitOpsCreateRow])

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
      const r = await changeRequestsApi.validateChangeRequest({
        operationType,
        requestedValue,
        targetEnvironment: createTargetEnv,
      })
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
        targetEnvironment: createTargetEnv,
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

  const runGitOpsDryRun = async () => {
    if (!gitOpsDryRunRow) return
    setGitOpsBusy(true)
    setGitOpsError(null)
    try {
      const r = await changeRequestsApi.gitopsDryRun(gitOpsDryRunRow.id, {
        targetEnvironment: gitOpsDryRunEnv,
      })
      setGitOpsDryRunResult(r)
    } catch (e: unknown) {
      const api = extractApiErrorFields(e)
      setGitOpsError(api ? `${api.errorCode}: ${api.message}` : readableErrorMessage(e))
    } finally {
      setGitOpsBusy(false)
    }
  }

  const runGitOpsCreatePr = async () => {
    if (!gitOpsCreateRow) return
    const env = (gitOpsCreateRow.targetEnvironment || 'staging').trim()
    const prodHigh = env.toLowerCase() === 'prod' && isHighSeverity(gitOpsCreateRow)
    if (prodHigh && gitOpsCreateConfirm.trim() !== 'CONFIRM') return
    setGitOpsBusy(true)
    setGitOpsError(null)
    try {
      const idempotencyKey =
        typeof crypto !== 'undefined' && 'randomUUID' in crypto
          ? crypto.randomUUID()
          : `idem-${Date.now()}`
      const r = await changeRequestsApi.gitopsCreatePr(gitOpsCreateRow.id, {
        targetEnvironment: env,
        idempotencyKey,
        confirmation: prodHigh ? 'CONFIRM' : undefined,
      })
      setGitOpsCreateRow(null)
      setGitOpsCreateFlash({
        url: r.providerPrUrl ?? '',
        status: r.status,
      })
    } catch (e: unknown) {
      const api = extractApiErrorFields(e)
      setGitOpsError(api ? `${api.errorCode}: ${api.message}` : readableErrorMessage(e))
    } finally {
      setGitOpsBusy(false)
    }
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

      {gitOpsCreateFlash ? (
        <Card className="border-sky-200 bg-sky-50/80 text-sm text-sky-950">
          <p className="font-semibold">GitOps PR created ({gitOpsCreateFlash.status})</p>
          {gitOpsCreateFlash.url ? (
            <p className="mt-1 text-xs break-all">
              <a href={gitOpsCreateFlash.url} className="text-sky-900 underline" target="_blank" rel="noreferrer">
                {gitOpsCreateFlash.url}
              </a>
            </p>
          ) : null}
        </Card>
      ) : null}

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

      {!gitOpsUi && isEnterpriseAdminWriteEnabled() ? (
        <p className="text-xs text-slate-600">
          GitOps PR actions are hidden. Enable{' '}
          <code className="rounded bg-slate-100 px-1">FRONTEND_ENTERPRISE_GITOPS_PR_ENABLED</code> and matching backend{' '}
          <code className="rounded bg-slate-100 px-1">ADMIN_GITOPS_PR_ENABLED</code> to dry-run patches and open PRs for
          approved requests.
        </p>
      ) : null}

      {gitOpsUi && !gitOpsRbacUi && isEnterpriseAdminWriteEnabled() ? (
        <p className="text-xs text-amber-800">
          Approved <strong>admin RBAC role</strong> GitOps proposals are hidden until{' '}
          <code className="rounded bg-amber-100 px-1">FRONTEND_GITOPS_RBAC_ROLE_REQUESTS_ENABLED</code> and backend{' '}
          <code className="rounded bg-amber-100 px-1">ADMIN_GITOPS_RBAC_ROLE_REQUESTS_ENABLED</code> are enabled (Faz 87).
        </p>
      ) : null}

      <Card className="space-y-3">
        <p className="text-xs font-semibold uppercase text-slate-500">Create request</p>
        {!canCreateFlow ? (
          <p className="text-xs text-slate-600">
            Your account does not have <code className="rounded bg-slate-100 px-1">admin:change-request:create</code>.
          </p>
        ) : null}
        {canCreateFlow && !canOperateType ? (
          <p className="text-xs text-amber-800">
            Selected operation requires an additional rollout permission (for example{' '}
            <code className="rounded bg-amber-100 px-1">admin:merge:change-request:create</code>).
          </p>
        ) : null}
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <label className="block text-xs text-slate-600">
            Operation
            <select
              className="mt-1 w-full rounded border border-slate-200 px-2 py-1.5 text-sm"
              value={operationType}
              onChange={(e) => setOperationType(e.target.value)}
              disabled={busy || !canCreateFlow}
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
              disabled={busy || !canCreateFlow}
            >
              {opMeta.valueOptions.map((v) => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-xs text-slate-600">
            Target environment
            <select
              className="mt-1 w-full rounded border border-slate-200 px-2 py-1.5 text-sm"
              value={createTargetEnv}
              onChange={(e) => setCreateTargetEnv(e.target.value)}
              disabled={busy || !canCreateFlow}
            >
              {changeRequestsApi.GITOPS_TARGET_ENVIRONMENTS.map((e) => (
                <option key={e} value={e}>
                  {e}
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
            disabled={busy || !canCreateFlow || !canOperateType}
          >
            Validate
          </button>
          <button
            type="button"
            className="rounded bg-primary-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            onClick={() => void onCreate()}
            disabled={busy || !canCreate || !canOperateType}
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
                  <th className="py-2 pr-2">Env</th>
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
                      <td className="py-2 pr-2 font-mono">{row.targetEnvironment ?? '—'}</td>
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
                        {canActPending && canApprove ? (
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
                        ) : null}
                        {canActPending && canReject ? (
                          <button
                            type="button"
                            className="text-amber-800 underline disabled:opacity-50"
                            disabled={busy}
                            onClick={() => openReject(row)}
                          >
                            Reject
                          </button>
                        ) : null}
                        {row.status === 'PENDING' &&
                        (canCancelScoped || (selfCreated && canCancelSelf)) ? (
                          <button
                            type="button"
                            className="text-red-700 underline disabled:opacity-50"
                            disabled={busy}
                            onClick={() => void onCancel(row.id)}
                          >
                            Cancel
                          </button>
                        ) : null}
                        {row.status === 'APPROVED' &&
                        gitOpsUi &&
                        (!changeRequestsApi.isAdminRbacRoleChangeRequestOperation(row.operationType) ||
                          gitOpsRbacUi) ? (
                          <span className="inline-flex flex-wrap gap-x-2 gap-y-1">
                            {canGitOpsDryRun ? (
                              <button
                                type="button"
                                className="text-primary-700 underline disabled:opacity-50"
                                disabled={busy || gitOpsBusy}
                                onClick={() => setGitOpsDryRunRow(row)}
                              >
                                Dry-run GitOps
                              </button>
                            ) : null}
                            {canGitOpsCreate ? (
                              <button
                                type="button"
                                className="text-primary-700 underline disabled:opacity-50"
                                disabled={busy || gitOpsBusy}
                                onClick={() => setGitOpsCreateRow(row)}
                              >
                                Create GitOps PR
                              </button>
                            ) : null}
                            {!canGitOpsDryRun && !canGitOpsCreate ? (
                              <span className="text-slate-500">GitOps (no permission)</span>
                            ) : null}
                          </span>
                        ) : row.status === 'APPROVED' &&
                          gitOpsUi &&
                          changeRequestsApi.isAdminRbacRoleChangeRequestOperation(row.operationType) &&
                          !gitOpsRbacUi ? (
                          <span className="text-xs text-slate-500">RBAC GitOps proposals off</span>
                        ) : row.status === 'APPROVED' ? (
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

      <Modal
        open={!!gitOpsDryRunRow}
        title="GitOps dry-run"
        onClose={() => {
          if (!gitOpsBusy) {
            setGitOpsDryRunRow(null)
            setGitOpsDryRunResult(null)
            setGitOpsError(null)
          }
        }}
      >
        {gitOpsDryRunRow ? (
          <div className="space-y-3 text-xs text-slate-800">
            <p className="rounded border border-slate-200 bg-slate-50 p-2 text-slate-700">
              Preview only — no pull request is created. Values come from packaged baselines; production paths are
              allow-listed in identity-service.
            </p>
            {gitOpsDryRunRow &&
            changeRequestsApi.isAdminRbacRoleChangeRequestOperation(gitOpsDryRunRow.operationType) ? (
              <p className="rounded border border-amber-200 bg-amber-50 p-2 text-amber-950">
                RBAC requests append a row to <code className="font-mono">admin-rbac-overrides.yaml</code> only. This
                dry-run does <strong>not</strong> grant or revoke roles at runtime.
              </p>
            ) : null}
            {gitOpsError ? <ErrorAlert message={gitOpsError} /> : null}
            <label className="block">
              Target environment
              <select
                className="mt-1 w-full rounded border border-slate-300 px-2 py-1 text-sm"
                value={gitOpsDryRunEnv}
                onChange={(e) => setGitOpsDryRunEnv(e.target.value)}
                disabled={gitOpsBusy}
              >
                {changeRequestsApi.GITOPS_TARGET_ENVIRONMENTS.map((e) => (
                  <option key={e} value={e}>
                    {e}
                  </option>
                ))}
              </select>
            </label>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className="rounded bg-slate-800 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                disabled={gitOpsBusy}
                onClick={() => void runGitOpsDryRun()}
              >
                Run dry-run
              </button>
            </div>
            {gitOpsDryRunResult ? (
              <div className="space-y-2">
                {gitOpsDryRunResult.warnings?.length ? (
                  <div className="rounded border border-amber-200 bg-amber-50 p-2 text-amber-950">
                    <p className="font-semibold">Warnings</p>
                    <ul className="list-inside list-disc">
                      {gitOpsDryRunResult.warnings.map((w) => (
                        <li key={w}>{w}</li>
                      ))}
                    </ul>
                  </div>
                ) : null}
                <p className="font-semibold text-slate-900">Diff preview</p>
                <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded border border-slate-200 bg-slate-50 p-2 text-[11px]">
                  {gitOpsDryRunResult.diffPreview}
                </pre>
              </div>
            ) : null}
          </div>
        ) : null}
      </Modal>

      <Modal
        open={!!gitOpsCreateRow}
        title="Create GitOps PR"
        onClose={() => {
          if (!gitOpsBusy) {
            setGitOpsCreateRow(null)
            setGitOpsError(null)
          }
        }}
      >
        {gitOpsCreateRow ? (
          <div className="space-y-3 text-xs text-slate-800">
            <p className="rounded border border-amber-200 bg-amber-50 p-2 text-amber-950">
              Opens a pull request via the configured provider (mock or GitHub). No secrets are shown in the UI; runtime
              config is not applied automatically.
            </p>
            {gitOpsCreateRow &&
            changeRequestsApi.isAdminRbacRoleChangeRequestOperation(gitOpsCreateRow.operationType) ? (
              <p className="rounded border border-amber-200 bg-amber-50 p-2 text-amber-950">
                RBAC PRs only update the governance manifest <code className="font-mono">admin-rbac-overrides.yaml</code>
                . Merging the PR does <strong>not</strong> apply roles until a future runtime ingestion phase.
              </p>
            ) : null}
            {gitOpsError ? <ErrorAlert message={gitOpsError} /> : null}
            <p>
              Change request <span className="font-mono">{gitOpsCreateRow.id}</span> — environment{' '}
              <span className="font-mono">{gitOpsCreateRow.targetEnvironment ?? 'staging'}</span> (must match the
              request).
            </p>
            {isHighSeverity(gitOpsCreateRow) &&
            (gitOpsCreateRow.targetEnvironment ?? '').toLowerCase() === 'prod' ? (
              <label className="block">
                Type CONFIRM for prod + HIGH severity
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-2 py-1 font-mono text-sm"
                  value={gitOpsCreateConfirm}
                  onChange={(e) => setGitOpsCreateConfirm(e.target.value)}
                  autoComplete="off"
                  disabled={gitOpsBusy}
                />
              </label>
            ) : null}
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                className="rounded border border-slate-300 px-3 py-1.5 text-sm"
                disabled={gitOpsBusy}
                onClick={() => setGitOpsCreateRow(null)}
              >
                Close
              </button>
              <button
                type="button"
                className="rounded bg-primary-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                disabled={
                  gitOpsBusy ||
                  (isHighSeverity(gitOpsCreateRow) &&
                    (gitOpsCreateRow.targetEnvironment ?? '').toLowerCase() === 'prod' &&
                    gitOpsCreateConfirm.trim() !== 'CONFIRM')
                }
                onClick={() => void runGitOpsCreatePr()}
              >
                Create PR
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
                <dt className="text-slate-500">Target environment</dt>
                <dd className="font-mono">{detailRow.targetEnvironment ?? '—'}</dd>
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

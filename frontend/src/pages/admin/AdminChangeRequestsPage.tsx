import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import { EmptyState } from '../../shared/components/EmptyState'
import { ApiError, readableErrorMessage } from '../../shared/api/api-client'
import {
  isEnterpriseAdminWriteEnabled,
  isEnterpriseGitOpsPrUiEnabled,
  isEnterpriseGitOpsRbacRoleRequestsUiEnabled,
} from '../../shared/config/admin-feature-flags'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_CHANGE_REQUEST_CREATE,
  canCreateChangeRequestForOperation,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import * as changeRequestsApi from '../../features/admin/enterprise/change-requests-api'
import type { ChangeRequestStatusFilter, ValidateChangeRequestResponse } from '../../features/admin/enterprise/change-requests-api'
import { ChangeRequestListTable } from '../../features/admin/change-requests/ChangeRequestListTable'
import { GitOpsDisabledBanner } from '../../features/admin/change-requests/GitOpsDisabledBanner'
import { useChangeRequestList } from '../../features/admin/change-requests/use-change-request'

const STATUS_FILTERS: { value: ChangeRequestStatusFilter; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'ALL', label: 'All' },
]

export function AdminChangeRequestsPage() {
  const [searchParams] = useSearchParams()
  const preOp = searchParams.get('op') ?? ''
  const preVal = searchParams.get('val') ?? ''
  const currentUser = useAuthStore((s) => s.user)
  const canCreateFlow = hasPlatformPermission(currentUser, PERM_CHANGE_REQUEST_CREATE)
  const gitOpsFlags = {
    gitOpsUi: isEnterpriseGitOpsPrUiEnabled(),
    gitOpsRbacUi: isEnterpriseGitOpsRbacRoleRequestsUiEnabled(),
  }

  const [statusFilter, setStatusFilter] = useState<ChangeRequestStatusFilter>('PENDING')
  const { items, loading, error, refresh } = useChangeRequestList(statusFilter)

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

  useEffect(() => {
    setRequestedValue((prev) => (opMeta.valueOptions.includes(prev) ? prev : opMeta.valueOptions[0]))
    setValidateResult(null)
    setValidateOk(false)
    setConfirmation('')
  }, [operationType, opMeta])

  if (!isEnterpriseAdminWriteEnabled()) {
    return (
      <div className="space-y-4">
        <PageHeader title="Enterprise change requests" subtitle="Enterprise configuration change workflow." />
        <GitOpsDisabledBanner reason="write" />
      </div>
    )
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

  return (
    <div className="space-y-6">
      <PageHeader
        title="Enterprise change requests"
        subtitle="Validate impact, open pending requests, approve via four-eyes, then GitOps PR (no runtime mutation)."
      />
      <p className="text-xs text-slate-600">
        <Link to="/app/admin/overview" className="text-primary-700 underline">
          Admin overview
        </Link>
        {!gitOpsFlags.gitOpsUi ? (
          <span className="ml-2 text-slate-500">Ã‚Â· GitOps PR UI disabled (feature flag)</span>
        ) : null}
      </p>

      {actionError ? <ErrorAlert message={actionError} /> : null}
      {!gitOpsFlags.gitOpsUi ? <GitOpsDisabledBanner reason="gitops" /> : null}

      <Card className="space-y-3">
        <p className="text-xs font-semibold uppercase text-slate-500">Create request</p>
        {!canCreateFlow ? (
          <p className="text-xs text-slate-600">Missing admin:change-request:create permission.</p>
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
              <li>Severity: {String(validateResult.impactSummary.severity ?? 'Ã¢â‚¬â€')}</li>
              <li>{String(validateResult.impactSummary.description ?? '')}</li>
            </ul>
            {needsConfirm ? (
              <label className="mt-3 block text-slate-700">
                Type CONFIRM for high-severity requests
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-2 py-1 font-mono text-sm"
                  value={confirmation}
                  onChange={(e) => setConfirmation(e.target.value)}
                  autoComplete="off"
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
                  statusFilter === f.value ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-700'
                }`}
                onClick={() => setStatusFilter(f.value)}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>
        {loading ? <LoadingState label="Loading change requests" /> : null}
        {error ? <ErrorAlert message={error} /> : null}
        {!loading && items && items.length === 0 ? (
          <EmptyState title="No requests" message="No change requests match this filter." />
        ) : null}
        {!loading && items && items.length > 0 ? (
          <ChangeRequestListTable items={items} gitOpsFlags={gitOpsFlags} />
        ) : null}
      </Card>
    </div>
  )
}

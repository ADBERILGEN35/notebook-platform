import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import {
  isEnterpriseAdminWriteEnabled,
  isEnterpriseGitOpsPrUiEnabled,
  isEnterpriseGitOpsRbacRoleRequestsUiEnabled,
} from '../../shared/config/admin-feature-flags'
import { PERM_CHANGE_REQUEST_GITOPS_CREATE, hasPlatformPermission } from '../../features/admin/access/admin-permissions'
import { useAuthStore } from '../../features/auth/auth-store'
import * as changeRequestsApi from '../../features/admin/enterprise/change-requests-api'
import { GitOpsPrStateCard } from '../../features/admin/change-requests/GitOpsPrStateCard'
import { GitOpsDisabledBanner } from '../../features/admin/change-requests/GitOpsDisabledBanner'
import { readDryRunResult } from '../../features/admin/change-requests/dry-run-storage'
import { useChangeRequestById } from '../../features/admin/change-requests/use-change-request'
import { deriveGitOpsPrState } from '../../features/admin/change-requests/gitops-pr-state'
import {
  extractApiErrorFields,
  gitOpsAvailableForRow,
  isHighSeverity,
  isRbacRoleRequest,
} from '../../features/admin/change-requests/change-request-utils'
import { readableErrorMessage } from '../../shared/api/api-client'

export function AdminChangeRequestGitOpsPage() {
  const { id } = useParams<{ id: string }>()
  const user = useAuthStore((s) => s.user)
  const { row, loading, error } = useChangeRequestById(id)
  const dryRun = id ? readDryRunResult(id) : null
  const [creating, setCreating] = useState(false)
  const [prUrl, setPrUrl] = useState<string | null>(null)
  const [prError, setPrError] = useState<string | null>(null)
  const [confirm, setConfirm] = useState('')

  const gitOpsUi = isEnterpriseGitOpsPrUiEnabled()
  const gitOpsRbacUi = isEnterpriseGitOpsRbacRoleRequestsUiEnabled()
  const canCreate = hasPlatformPermission(user, PERM_CHANGE_REQUEST_GITOPS_CREATE)

  if (!isEnterpriseAdminWriteEnabled()) return <GitOpsDisabledBanner reason="write" />
  if (!gitOpsUi) {
    return (
      <div className="space-y-4">
        <PageHeader title="GitOps handoff" />
        <GitOpsDisabledBanner reason="gitops" />
      </div>
    )
  }

  if (loading) return <LoadingState label="Loading" />
  if (error) return <ErrorAlert message={error} />
  if (!row) return <p className="text-sm">Not found</p>

  if (isRbacRoleRequest(row) && !gitOpsRbacUi) {
    return <GitOpsDisabledBanner reason="rbac-gitops" />
  }

  const state = deriveGitOpsPrState({
    gitOpsEnabled: gitOpsUi,
    rowApproved: row.status === 'APPROVED',
    dryRunCompleted: !!dryRun,
    creating,
    prUrl,
    lastError: prError,
    rbacGitOpsEnabled: gitOpsRbacUi,
    isRbacRequest: isRbacRoleRequest(row),
  })

  const createPr = async () => {
    const env = (row.targetEnvironment || 'staging').trim()
    if (isHighSeverity(row) && env.toLowerCase() === 'prod' && confirm.trim() !== 'CONFIRM') return
    setCreating(true)
    setPrError(null)
    try {
      const idempotencyKey =
        typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `idem-${Date.now()}`
      const r = await changeRequestsApi.gitopsCreatePr(row.id, {
        targetEnvironment: env,
        idempotencyKey,
        confirmation: isHighSeverity(row) && env.toLowerCase() === 'prod' ? 'CONFIRM' : undefined,
      })
      setPrUrl(r.providerPrUrl)
    } catch (e: unknown) {
      const api = extractApiErrorFields(e)
      setPrError(api ? `${api.errorCode}: ${api.message}` : readableErrorMessage(e))
    } finally {
      setCreating(false)
    }
  }

  const gitOk = gitOpsAvailableForRow(row, { gitOpsUi, gitOpsRbacUi })

  return (
    <div className="space-y-6" data-testid="gitops-handoff-page">
      <PageHeader
        title="GitOps handoff"
        subtitle="Dry-run, review diff, create PR â€” no runtime apply."
        actions={
          <Link to={`/app/admin/change-requests/${row.id}`} className="text-sm text-primary-700 underline">
            Detail
          </Link>
        }
      />

      <GitOpsPrStateCard state={state} prUrl={prUrl} errorMessage={prError} />

      <Card className="flex flex-wrap gap-3 text-sm">
        <Link to={`/app/admin/change-requests/${row.id}/dry-run`} className="text-primary-700 underline">
          Dry-run preview
        </Link>
        <Link to={`/app/admin/change-requests/${row.id}/diff`} className="text-primary-700 underline">
          YAML diff viewer
        </Link>
      </Card>

      {gitOk && canCreate && state === 'ready' ? (
        <Card className="space-y-2 text-sm">
          {isHighSeverity(row) && (row.targetEnvironment ?? '').toLowerCase() === 'prod' ? (
            <label className="block text-xs">
              Type CONFIRM for prod + HIGH
              <input className="mt-1 w-full rounded border px-2 py-1 font-mono" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
            </label>
          ) : null}
          <button
            type="button"
            className="rounded bg-primary-600 px-3 py-1.5 text-white disabled:opacity-50"
            disabled={creating}
            onClick={() => void createPr()}
          >
            Create GitOps PR
          </button>
        </Card>
      ) : null}
    </div>
  )
}

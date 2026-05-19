import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import {
  isEnterpriseAdminWriteEnabled,
  isEnterpriseGitOpsPrUiEnabled,
} from '../../shared/config/admin-feature-flags'
import { PERM_CHANGE_REQUEST_GITOPS_DRY_RUN, hasPlatformPermission } from '../../features/admin/access/admin-permissions'
import { useAuthStore } from '../../features/auth/auth-store'
import * as changeRequestsApi from '../../features/admin/enterprise/change-requests-api'
import { GITOPS_TARGET_ENVIRONMENTS } from '../../features/admin/enterprise/change-requests-api'
import { DryRunWarningList } from '../../features/admin/change-requests/DryRunWarningList'
import { GitOpsDisabledBanner } from '../../features/admin/change-requests/GitOpsDisabledBanner'
import { stashDryRunResult } from '../../features/admin/change-requests/dry-run-storage'
import { useChangeRequestById } from '../../features/admin/change-requests/use-change-request'
import { extractApiErrorFields } from '../../features/admin/change-requests/change-request-utils'
import { readableErrorMessage } from '../../shared/api/api-client'
import { isRbacRoleRequest } from '../../features/admin/change-requests/change-request-utils'
import { RbacOverrideDiffPanel } from '../../features/admin/change-requests/RbacOverrideDiffPanel'
import { GitOpsDiffViewer } from '../../features/admin/change-requests/GitOpsDiffViewer'

export function AdminChangeRequestDryRunPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const { row, loading, error } = useChangeRequestById(id)
  const [env, setEnv] = useState('staging')
  const [busy, setBusy] = useState(false)
  const [runError, setRunError] = useState<string | null>(null)
  const [result, setResult] = useState<changeRequestsApi.GitOpsDryRunResponse | null>(null)

  const gitOpsOn = isEnterpriseGitOpsPrUiEnabled()
  const canDryRun = hasPlatformPermission(user, PERM_CHANGE_REQUEST_GITOPS_DRY_RUN)

  if (!isEnterpriseAdminWriteEnabled()) {
    return <GitOpsDisabledBanner reason="write" />
  }

  if (!gitOpsOn) {
    return (
      <div className="space-y-4">
        <PageHeader title="GitOps dry-run" />
        <GitOpsDisabledBanner reason="gitops" />
      </div>
    )
  }

  if (loading) return <LoadingState label="Loading request" />
  if (error) return <ErrorAlert message={error} />
  if (!row) return <p className="text-sm text-slate-600">Request not found.</p>

  const run = async () => {
    setBusy(true)
    setRunError(null)
    try {
      const r = await changeRequestsApi.gitopsDryRun(row.id, { targetEnvironment: env })
      setResult(r)
      stashDryRunResult(row.id, r)
    } catch (e: unknown) {
      const api = extractApiErrorFields(e)
      setRunError(api ? `${api.errorCode}: ${api.message}` : readableErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-6" data-testid="gitops-dry-run-page">
      <PageHeader
        title="GitOps dry-run preview"
        subtitle="Preview only Ã¢â‚¬â€ no pull request and no runtime mutation."
        actions={
          <Link to={`/app/admin/change-requests/${row.id}`} className="text-sm text-primary-700 underline">
            Back to detail
          </Link>
        }
      />

      {!canDryRun ? <p className="text-xs text-amber-800">Missing admin:change-request:gitops:dry-run permission.</p> : null}

      <Card className="space-y-3 text-sm">
        <p className="rounded border border-slate-200 bg-slate-50 p-2 text-xs text-slate-700">
          Dry-run computes a patch preview from packaged baselines. Values are masked in the diff viewer.
        </p>
        <label className="block text-xs">
          Target environment
          <select className="mt-1 w-full rounded border px-2 py-1" value={env} onChange={(e) => setEnv(e.target.value)} disabled={busy}>
            {GITOPS_TARGET_ENVIRONMENTS.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          className="rounded bg-slate-800 px-3 py-1.5 text-sm text-white disabled:opacity-50"
          disabled={busy || !canDryRun || row.status !== 'APPROVED'}
          onClick={() => void run()}
        >
          Run dry-run
        </button>
        {row.status !== 'APPROVED' ? (
          <p className="text-xs text-amber-800">Request must be APPROVED before dry-run.</p>
        ) : null}
      </Card>

      {runError ? <ErrorAlert message={runError} /> : null}

      {result ? (
        <Card className="space-y-3">
          <p className="text-sm font-semibold text-slate-900">Result: {result.provider}</p>
          <DryRunWarningList warnings={result.warnings ?? []} />
          {isRbacRoleRequest(row) ? (
            <RbacOverrideDiffPanel row={row} dryRun={result} />
          ) : (
            <>
              <GitOpsDiffViewer
                diffPreview={result.diffPreview}
                environment={result.targetEnvironment}
                operationType={row.operationType}
                mode="unified"
              />
              <button
                type="button"
                className="text-sm text-primary-700 underline"
                onClick={() => navigate(`/app/admin/change-requests/${row.id}/diff`)}
              >
                Open full diff viewer
              </button>
            </>
          )}
        </Card>
      ) : null}
    </div>
  )
}

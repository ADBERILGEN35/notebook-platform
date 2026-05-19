import { Link, useParams } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import { isEnterpriseAdminWriteEnabled, isEnterpriseGitOpsPrUiEnabled } from '../../shared/config/admin-feature-flags'
import { GitOpsDiffViewer } from '../../features/admin/change-requests/GitOpsDiffViewer'
import { GitOpsDisabledBanner } from '../../features/admin/change-requests/GitOpsDisabledBanner'
import { readDryRunResult } from '../../features/admin/change-requests/dry-run-storage'
import { useChangeRequestById } from '../../features/admin/change-requests/use-change-request'
import { isRbacRoleRequest } from '../../features/admin/change-requests/change-request-utils'
import { RbacOverrideDiffPanel } from '../../features/admin/change-requests/RbacOverrideDiffPanel'

export function AdminChangeRequestDiffPage() {
  const { id } = useParams<{ id: string }>()
  const { row, loading, error } = useChangeRequestById(id)
  const dryRun = id ? readDryRunResult(id) : null

  if (!isEnterpriseAdminWriteEnabled()) return <GitOpsDisabledBanner reason="write" />
  if (!isEnterpriseGitOpsPrUiEnabled()) {
    return (
      <div className="space-y-4">
        <PageHeader title="YAML diff" />
        <GitOpsDisabledBanner reason="gitops" />
      </div>
    )
  }

  if (loading) return <LoadingState label="Loading" />
  if (error) return <ErrorAlert message={error} />
  if (!row) return <p className="text-sm">Not found</p>

  return (
    <div className="space-y-4" data-testid="gitops-diff-page">
      <PageHeader
        title="GitOps YAML diff"
        subtitle="Side-by-side and unified views with secret masking."
        actions={
          <Link to={`/app/admin/change-requests/${row.id}/dry-run`} className="text-sm text-primary-700 underline">
            Dry-run
          </Link>
        }
      />

      <div className="sticky top-0 z-10 flex flex-wrap gap-2 border-b border-slate-200 bg-white/95 py-2 backdrop-blur">
        <Link to={`/app/admin/change-requests/${row.id}/gitops`} className="text-xs text-primary-700 underline">
          GitOps hub
        </Link>
      </div>

      {!dryRun ? (
        <Card>
          <p className="text-sm text-slate-600">Run dry-run first to load a diff preview.</p>
          <Link to={`/app/admin/change-requests/${row.id}/dry-run`} className="mt-2 inline-block text-sm text-primary-700 underline">
            Go to dry-run
          </Link>
        </Card>
      ) : isRbacRoleRequest(row) ? (
        <RbacOverrideDiffPanel row={row} dryRun={dryRun} />
      ) : (
        <>
          <GitOpsDiffViewer
            diffPreview={dryRun.diffPreview}
            environment={dryRun.targetEnvironment}
            operationType={row.operationType}
            filePath={dryRun.changedFiles?.[0]?.path}
            mode="unified"
          />
          <div className="hidden md:block">
            <GitOpsDiffViewer
              diffPreview={dryRun.diffPreview}
              environment={dryRun.targetEnvironment}
              operationType={row.operationType}
              mode="side-by-side"
            />
          </div>
        </>
      )}
    </div>
  )
}

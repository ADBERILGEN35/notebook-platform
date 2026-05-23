import type { ChangeRequestItem, GitOpsDryRunResponse } from '../enterprise/change-requests-api'
import { maskUserId, isPlatformAdminRoleRequest } from './change-request-utils'
import { GitOpsDiffViewer } from './GitOpsDiffViewer'

export function RbacOverrideDiffPanel({
  row,
  dryRun,
}: {
  row: ChangeRequestItem
  dryRun?: GitOpsDryRunResponse | null
}) {
  const highRisk = isPlatformAdminRoleRequest(row)
  const targetUser =
    (row.validationResult as Record<string, unknown> | null)?.targetUserId ??
    (row.impactSummary as Record<string, unknown> | null)?.targetUserId

  return (
    <div className="space-y-3" data-testid="rbac-override-diff-panel">
      <p className="text-sm font-semibold text-slate-900">RBAC override manifest</p>
      <p className="text-xs text-slate-600">
        Proposed change to <code className="rounded bg-slate-100 px-1">admin-rbac-overrides.yaml</code> only. No
        runtime role mutation; no raw IdP claims.
      </p>

      {highRisk ? (
        <p
          className="rounded border border-red-200 bg-red-50 p-3 text-xs font-medium text-red-950"
          role="alert"
        >
          HIGH risk: PLATFORM_ADMIN grant/revoke — require four-eyes approval and least-privilege review.
        </p>
      ) : null}

      <ul className="text-xs text-slate-700 space-y-1" aria-label="Role change summary">
        <li>Operation: {row.operationType}</li>
        <li>Requested value: {row.requestedValue}</li>
        <li>Target user: {maskUserId(String(targetUser ?? ''))}</li>
      </ul>

      {dryRun?.diffPreview ? (
        <GitOpsDiffViewer
          diffPreview={dryRun.diffPreview}
          environment={dryRun.targetEnvironment}
          operationType={row.operationType}
          filePath="admin-rbac-overrides.yaml"
          mode="unified"
        />
      ) : (
        <p className="text-xs text-slate-500">Run dry-run to preview YAML diff.</p>
      )}
    </div>
  )
}

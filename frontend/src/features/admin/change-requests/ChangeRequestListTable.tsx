import { Link } from 'react-router-dom'
import { ResponsiveTableShell } from '../../../shared/components/ResponsiveTableShell'
import type { ChangeRequestItem } from '../enterprise/change-requests-api'
import {
  ChangeRequestStatusBadge,
  GitOpsStateBadge,
  OperationTypeBadge,
  SeverityBadge,
} from './ChangeRequestBadges'
import { gitOpsAvailableForRow, maskUserId, rowSeverity } from './change-request-utils'
import { readDryRunResult } from './dry-run-storage'
import { deriveGitOpsPrState } from './gitops-pr-state'

type ChangeRequestListTableProps = {
  items: ChangeRequestItem[]
  gitOpsFlags: { gitOpsUi: boolean; gitOpsRbacUi: boolean }
}

export function ChangeRequestListTable({ items, gitOpsFlags }: ChangeRequestListTableProps) {
  return (
    <ResponsiveTableShell label="Change requests" testId="change-request-list-table">
      <table className="min-w-full text-left text-xs text-slate-800">
        <thead>
          <tr className="border-b border-slate-200 text-slate-500">
            <th className="py-2 pr-2">Created</th>
            <th className="py-2 pr-2">Status</th>
            <th className="py-2 pr-2">Operation</th>
            <th className="py-2 pr-2">Severity</th>
            <th className="py-2 pr-2">Requester</th>
            <th className="py-2 pr-2">Approver</th>
            <th className="py-2 pr-2">GitOps</th>
            <th className="py-2 pr-2" />
          </tr>
        </thead>
        <tbody>
          {items.map((row) => {
            const gitOk = gitOpsAvailableForRow(row, gitOpsFlags)
            const dryRun = readDryRunResult(row.id)
            const prState = deriveGitOpsPrState({
              gitOpsEnabled: gitOpsFlags.gitOpsUi,
              rowApproved: row.status === 'APPROVED',
              dryRunCompleted: !!dryRun,
              creating: false,
              prUrl: null,
              lastError: null,
              rbacGitOpsEnabled: gitOpsFlags.gitOpsRbacUi,
              isRbacRequest:
                row.operationType.includes('RBAC') || row.operationType.includes('ROLE'),
            })
            return (
              <tr key={row.id} className="border-b border-slate-100">
                <td className="py-2 pr-2 whitespace-nowrap">{new Date(row.createdAt).toLocaleString()}</td>
                <td className="py-2 pr-2">
                  <ChangeRequestStatusBadge status={row.status} />
                </td>
                <td className="py-2 pr-2">
                  <OperationTypeBadge operationType={row.operationType} />
                </td>
                <td className="py-2 pr-2">
                  <SeverityBadge severity={rowSeverity(row)} />
                </td>
                <td className="py-2 pr-2 font-mono">{maskUserId(row.requestedByUserId)}</td>
                <td className="py-2 pr-2 font-mono">{maskUserId(row.decidedByUserId)}</td>
                <td className="py-2 pr-2">
                  {gitOk ? (
                    <GitOpsStateBadge state={prState} />
                  ) : row.status === 'APPROVED' ? (
                    <span className="text-slate-500">N/A</span>
                  ) : (
                    <span className="text-slate-400">—</span>
                  )}
                </td>
                <td className="py-2 pr-2 space-x-2 whitespace-nowrap">
                  <Link className="font-medium text-primary-700 hover:underline" to={`/app/admin/change-requests/${row.id}`}>
                    View
                  </Link>
                  {gitOk ? (
                    <Link
                      className="text-primary-700 hover:underline"
                      to={`/app/admin/change-requests/${row.id}/dry-run`}
                    >
                      Dry-run GitOps
                    </Link>
                  ) : null}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </ResponsiveTableShell>
  )
}

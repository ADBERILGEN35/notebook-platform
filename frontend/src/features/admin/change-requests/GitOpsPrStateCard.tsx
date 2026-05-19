import type { GitOpsPrUiState } from './gitops-pr-state'
import { GitOpsStateBadge } from './ChangeRequestBadges'

const descriptions: Record<GitOpsPrUiState, string> = {
  not_started: 'GitOps handoff not started for this approved request.',
  dry_run_required: 'Run a dry-run preview before creating a pull request.',
  ready: 'Dry-run completed — you may create a GitOps PR (no runtime apply).',
  creating: 'Creating pull request via configured provider…',
  created: 'Pull request created. Merge via your GitOps workflow.',
  failed: 'PR creation failed. Review the error and retry after fixing configuration.',
  disabled: 'GitOps PR UI is disabled in this environment (feature flag).',
  blocked: 'Request must be APPROVED before GitOps actions are available.',
}

export function GitOpsPrStateCard({
  state,
  prUrl,
  errorMessage,
}: {
  state: GitOpsPrUiState
  prUrl?: string | null
  errorMessage?: string | null
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 text-sm" data-testid="gitops-pr-state-card">
      <div className="flex flex-wrap items-center gap-2">
        <GitOpsStateBadge state={state} />
      </div>
      <p className="mt-2 text-slate-700">{descriptions[state]}</p>
      {state === 'created' && prUrl ? (
        <p className="mt-2 text-xs break-all">
          <a href={prUrl} target="_blank" rel="noreferrer" className="text-primary-700 underline">
            Open pull request
          </a>
        </p>
      ) : null}
      {errorMessage ? <p className="mt-2 text-xs text-red-800">{errorMessage}</p> : null}
      <p className="mt-2 text-xs text-slate-500">No runtime mutation — configuration changes apply only after GitOps merge.</p>
    </div>
  )
}

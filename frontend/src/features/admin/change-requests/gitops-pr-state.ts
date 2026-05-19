export type GitOpsPrUiState =
  | 'not_started'
  | 'dry_run_required'
  | 'ready'
  | 'creating'
  | 'created'
  | 'failed'
  | 'disabled'
  | 'blocked'

export function deriveGitOpsPrState(input: {
  gitOpsEnabled: boolean
  rowApproved: boolean
  dryRunCompleted: boolean
  creating: boolean
  prUrl: string | null
  lastError: string | null
  rbacGitOpsEnabled: boolean
  isRbacRequest: boolean
}): GitOpsPrUiState {
  if (!input.gitOpsEnabled) return 'disabled'
  if (!input.rowApproved) return 'blocked'
  if (input.isRbacRequest && !input.rbacGitOpsEnabled) return 'disabled'
  if (input.creating) return 'creating'
  if (input.prUrl) return 'created'
  if (input.lastError) return 'failed'
  if (!input.dryRunCompleted) return 'dry_run_required'
  return 'ready'
}

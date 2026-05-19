export function GitOpsDisabledBanner({ reason }: { reason: 'gitops' | 'rbac-gitops' | 'write' }) {
  if (reason === 'write') {
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700" data-testid="gitops-disabled-banner">
        <p className="font-semibold">Change requests UI disabled</p>
        <p className="mt-1 text-xs">Enable ENTERPRISE_ADMIN_WRITE_ENABLED (non-production) to use this area.</p>
      </div>
    )
  }
  if (reason === 'rbac-gitops') {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950" data-testid="gitops-disabled-banner">
        <p className="font-semibold">RBAC GitOps proposals disabled</p>
        <p className="mt-1 text-xs">
          Enable GITOPS_RBAC_ROLE_REQUESTS_ENABLED alongside enterprise GitOps UI for admin role grant/revoke PRs.
        </p>
      </div>
    )
  }
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700" data-testid="gitops-disabled-banner">
      <p className="font-semibold">GitOps PR UI disabled</p>
      <p className="mt-1 text-xs">
        Enable ENTERPRISE_GITOPS_PR_ENABLED and matching gateway ADMIN_GITOPS_PR_ENABLED to dry-run and create PRs.
      </p>
    </div>
  )
}

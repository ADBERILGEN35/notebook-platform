type AdminRunbookLinkProps = {
  docPath: string
  label: string
}

/** In-app runbook reference (repository path only; no external CDN). */
export function AdminRunbookLink({ docPath, label }: AdminRunbookLinkProps) {
  return (
    <p className="text-xs text-slate-600">
      <span className="font-medium text-slate-700">{label}:</span>{' '}
      <code className="rounded bg-slate-100 px-1 text-[11px]">{docPath}</code>
    </p>
  )
}

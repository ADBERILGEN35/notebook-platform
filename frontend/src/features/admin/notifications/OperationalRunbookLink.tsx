export function OperationalRunbookLink({ docPath, label }: { docPath: string; label?: string }) {
  return (
    <p className="text-xs text-slate-600">
      <span className="font-medium text-slate-700">{label ?? 'Runbook'}:</span>{' '}
      <code className="rounded bg-slate-100 px-1 text-[11px]">{docPath}</code>
    </p>
  )
}

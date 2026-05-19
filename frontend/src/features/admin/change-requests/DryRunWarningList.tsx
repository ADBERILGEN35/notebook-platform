export function DryRunWarningList({ warnings }: { warnings: string[] }) {
  if (!warnings.length) {
    return <p className="text-xs text-slate-600">No warnings from dry-run.</p>
  }
  return (
    <ul className="space-y-1 rounded border border-amber-200 bg-amber-50 p-3 text-xs text-amber-950" data-testid="dry-run-warnings">
      {warnings.map((w) => (
        <li key={w}>⚠ {w}</li>
      ))}
    </ul>
  )
}

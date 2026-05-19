export function DiffFileHeader({
  path,
  environment,
  operationType,
}: {
  path?: string
  environment?: string
  operationType?: string
}) {
  return (
    <nav className="flex flex-wrap items-center gap-2 text-xs text-slate-600" aria-label="Diff breadcrumbs">
      <span className="font-medium text-slate-800">Change requests</span>
      <span>/</span>
      {operationType ? <span className="font-mono">{operationType}</span> : null}
      {environment ? (
        <>
          <span>/</span>
          <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono">{environment}</span>
        </>
      ) : null}
      {path ? (
        <>
          <span>/</span>
          <span className="truncate font-mono text-slate-800">{path}</span>
        </>
      ) : null}
    </nav>
  )
}

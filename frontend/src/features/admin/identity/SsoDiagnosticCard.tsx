import { AdminDiagnosticPanel } from '../shared/AdminDiagnosticPanel'

type SsoDiagnosticRow = {
  label: string
  value: string
  ok?: boolean
}

export function SsoDiagnosticCard({ title, rows }: { title: string; rows: SsoDiagnosticRow[] }) {
  return (
    <AdminDiagnosticPanel title={title}>
      <ul className="space-y-2 text-sm">
        {rows.map((row) => (
          <li key={row.label} className="flex flex-wrap justify-between gap-2 border-b border-slate-100 pb-2">
            <span className="text-slate-600">{row.label}</span>
            <span
              className={
                row.ok === undefined
                  ? 'font-mono text-xs text-slate-800'
                  : row.ok
                    ? 'text-emerald-700'
                    : 'text-amber-800'
              }
            >
              {row.value}
            </span>
          </li>
        ))}
      </ul>
    </AdminDiagnosticPanel>
  )
}

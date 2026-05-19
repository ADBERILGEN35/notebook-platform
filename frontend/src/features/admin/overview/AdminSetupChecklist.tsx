import { AdminRunbookLink } from '../shared/AdminRunbookLink'
import type { SetupChecklistItem, SetupChecklistStatus } from './setup-checklist'

const statusStyles: Record<SetupChecklistStatus, string> = {
  completed: 'bg-emerald-100 text-emerald-800',
  warning: 'bg-amber-100 text-amber-900',
  blocked: 'bg-red-100 text-red-900',
  disabled: 'bg-slate-100 text-slate-600',
}

export function AdminSetupChecklist({ items }: { items: SetupChecklistItem[] }) {
  return (
    <ul className="space-y-3">
      {items.map((item) => (
        <li key={item.id} className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm font-medium text-slate-900">{item.label}</p>
            <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium uppercase ${statusStyles[item.status]}`}>
              {item.status}
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-600">{item.detail}</p>
          {item.runbookPath ? <AdminRunbookLink docPath={item.runbookPath} label="Runbook" /> : null}
        </li>
      ))}
    </ul>
  )
}

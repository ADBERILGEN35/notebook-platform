import type { AdminRbacUserRow } from '../rbac/admin-rbac-api'

export function RoleMappingTable({ rows }: { rows: AdminRbacUserRow[] }) {
  return (
    <div className="overflow-x-auto rounded border border-slate-200 bg-white">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2">User</th>
            <th className="px-3 py-2">Platform roles</th>
            <th className="px-3 py-2">Sources</th>
            <th className="px-3 py-2">Warnings</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={4} className="px-3 py-4 text-slate-500">
                No users match filters.
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr key={row.userId} className="border-t border-slate-100">
                <td className="px-3 py-2">
                  <p className="font-medium text-slate-900">{row.email}</p>
                  <p className="font-mono text-[11px] text-slate-500">{row.userId}</p>
                </td>
                <td className="px-3 py-2">{row.platformRoles.join(', ') || '—'}</td>
                <td className="px-3 py-2 text-xs">
                  {row.sources.map((s) => (
                    <p key={`${s.type}-${s.sourceName}`}>
                      {s.type}: {s.sourceName} → {s.roles.join(', ')}
                    </p>
                  ))}
                </td>
                <td className="px-3 py-2 text-xs text-amber-800">{row.warnings.join('; ') || '—'}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}

import { NavLink, Outlet } from 'react-router-dom'

const subLink = ({ isActive }: { isActive: boolean }) =>
  `rounded px-2 py-1 text-xs font-medium ${isActive ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'}`

export function AdminEnterpriseLayout() {
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2 border-b border-slate-200 pb-2">
        <NavLink to="/app/admin/enterprise" end className={subLink}>
          Overview
        </NavLink>
        <NavLink to="/app/admin/enterprise/security" className={subLink}>
          Security
        </NavLink>
        <NavLink to="/app/admin/enterprise/integrations" className={subLink}>
          Integrations
        </NavLink>
      </div>
      <Outlet />
    </div>
  )
}

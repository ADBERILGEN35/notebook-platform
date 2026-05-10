import { NavLink, Outlet } from 'react-router-dom'
import { isEnterpriseAdminWriteEnabled } from '../../shared/config/admin-feature-flags'

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `block rounded px-2 py-1.5 text-sm ${isActive ? 'bg-primary-100 font-medium text-primary-800' : 'text-slate-600 hover:bg-slate-100'}`

export function AdminLayout() {
  return (
    <div className="flex flex-col gap-4 lg:flex-row lg:gap-8">
      <nav className="shrink-0 lg:w-52">
        <p className="mb-2 text-xs font-semibold uppercase text-slate-500">Admin</p>
        <ul className="space-y-1">
          <li>
            <NavLink to="/app/admin" end className={linkClass}>
              Overview
            </NavLink>
          </li>
          <li>
            <NavLink to="/app/admin/audit" className={linkClass}>
              Audit Events
            </NavLink>
          </li>
          <li>
            <NavLink to="/app/admin/enterprise" end className={linkClass}>
              Enterprise Console
            </NavLink>
          </li>
          <li className="pl-3">
            <NavLink to="/app/admin/enterprise/security" className={linkClass}>
              Security
            </NavLink>
          </li>
          <li className="pl-3">
            <NavLink to="/app/admin/enterprise/integrations" className={linkClass}>
              Integrations
            </NavLink>
          </li>
          {isEnterpriseAdminWriteEnabled() ? (
            <li className="pl-3">
              <NavLink to="/app/admin/enterprise/change-requests" className={linkClass}>
                Change requests
              </NavLink>
            </li>
          ) : null}
        </ul>
      </nav>
      <div className="min-w-0 flex-1">
        <Outlet />
      </div>
    </div>
  )
}

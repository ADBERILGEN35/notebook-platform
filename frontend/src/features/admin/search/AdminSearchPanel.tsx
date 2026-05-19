import { Link } from 'react-router-dom'
import { Input } from '../../../shared/components/Input'

export type AdminSearchCategory = {
  id: string
  label: string
  description: string
  to: string
}

const DEFAULT_CATEGORIES: AdminSearchCategory[] = [
  {
    id: 'audit',
    label: 'Audit events',
    description: 'Filter by event type, actor, workspace, request id',
    to: '/app/admin/audit',
  },
  {
    id: 'identity',
    label: 'Identity & SSO',
    description: 'OIDC/SAML/SCIM posture and warnings',
    to: '/app/admin/identity',
  },
  {
    id: 'scim',
    label: 'SCIM provisioning',
    description: 'Sync runs, checkpoints, compatibility',
    to: '/app/admin/identity/scim',
  },
  {
    id: 'rbac',
    label: 'Role mapping',
    description: 'External group → platform role diagnostics',
    to: '/app/admin/identity/role-mapping',
  },
  {
    id: 'break-glass',
    label: 'Break-glass ops',
    description: 'Events, active sessions, revocation',
    to: '/app/admin/security/break-glass',
  },
]

type AdminSearchPanelProps = {
  query: string
  onQueryChange: (q: string) => void
  categories?: AdminSearchCategory[]
}

export function AdminSearchPanel({
  query,
  onQueryChange,
  categories = DEFAULT_CATEGORIES,
}: AdminSearchPanelProps) {
  const q = query.trim().toLowerCase()
  const filtered = q
    ? categories.filter(
        (c) =>
          c.label.toLowerCase().includes(q) ||
          c.description.toLowerCase().includes(q) ||
          c.id.includes(q),
      )
    : categories

  return (
    <div className="space-y-4">
      <label className="block text-sm font-medium text-slate-700">
        Admin search
        <Input
          className="mt-1"
          placeholder="Search diagnostics categories (audit, identity, SCIM, break-glass…)"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
        />
      </label>
      <ul className="space-y-2">
        {filtered.length === 0 ? (
          <li className="text-sm text-slate-500">No categories match your query.</li>
        ) : (
          filtered.map((cat) => (
            <li key={cat.id} className="rounded-lg border border-slate-200 p-3">
              <Link className="text-sm font-medium text-primary-700 hover:underline" to={cat.to}>
                {cat.label}
              </Link>
              <p className="mt-1 text-xs text-slate-600">{cat.description}</p>
            </li>
          ))
        )}
      </ul>
    </div>
  )
}

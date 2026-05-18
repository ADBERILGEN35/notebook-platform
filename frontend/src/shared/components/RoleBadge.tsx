import { StatusBadge } from './StatusBadge'

const roleLabels: Record<string, string> = {
  OWNER: 'Owner',
  ADMIN: 'Admin',
  MEMBER: 'Member',
  VIEWER: 'Viewer',
  EDITOR: 'Editor',
}

type RoleBadgeProps = {
  role: string
}

export function RoleBadge({ role }: RoleBadgeProps) {
  const label = roleLabels[role] ?? role.replace(/_/g, ' ').toLowerCase()
  const tone = role === 'OWNER' || role === 'ADMIN' ? 'success' : 'neutral'
  return <StatusBadge label={label} tone={tone} />
}

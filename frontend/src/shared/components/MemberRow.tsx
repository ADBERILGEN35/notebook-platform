import type { ReactNode } from 'react'
import { RoleBadge } from './RoleBadge'

type MemberRowProps = {
  name: string
  subtitle?: string
  role: string
  actions?: ReactNode
}

export function MemberRow({ name, subtitle, role, actions }: MemberRowProps) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-3">
      <div className="min-w-0">
        <p className="font-medium text-body-md text-on-surface">{name}</p>
        {subtitle ? <p className="text-label-md text-on-surface-variant">{subtitle}</p> : null}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <RoleBadge role={role} />
        {actions}
      </div>
    </li>
  )
}

import type { ReactNode } from 'react'
import { PageHeader } from '../../../shared/components/PageHeader'

type AdminPageShellProps = {
  title: string
  subtitle?: string
  actions?: ReactNode
  children: ReactNode
}

export function AdminPageShell({ title, subtitle, actions, children }: AdminPageShellProps) {
  return (
    <div className="space-y-4">
      <PageHeader title={title} subtitle={subtitle} actions={actions} />
      {children}
    </div>
  )
}

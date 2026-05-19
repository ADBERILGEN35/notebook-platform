import type { ReactNode } from 'react'
import { Card } from '../../../shared/components/Card'

export function AdminDiagnosticPanel({
  title,
  children,
}: {
  title: string
  children: ReactNode
}) {
  return (
    <Card className="space-y-3">
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      {children}
    </Card>
  )
}

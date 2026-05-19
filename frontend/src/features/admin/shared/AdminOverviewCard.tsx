import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Card } from '../../../shared/components/Card'

type AdminOverviewCardProps = {
  title: string
  description: string
  to?: string
  linkLabel?: string
  children?: ReactNode
}

export function AdminOverviewCard({ title, description, to, linkLabel, children }: AdminOverviewCardProps) {
  return (
    <Card className="space-y-2">
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      <p className="text-sm text-slate-600">{description}</p>
      {children}
      {to ? (
        <Link className="inline-block text-sm font-medium text-primary-600 hover:underline" to={to}>
          {linkLabel ?? 'Open'}
        </Link>
      ) : null}
    </Card>
  )
}

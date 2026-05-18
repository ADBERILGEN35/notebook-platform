import type { ReactNode } from 'react'
import { PanelCard } from '../../shared/components/PanelCard'
import { InlineStatus } from '../../shared/components/InlineStatus'

type SecurityMethodCardProps = {
  title: string
  description: string
  enabled: boolean
  children?: ReactNode
}

export function SecurityMethodCard({ title, description, enabled, children }: SecurityMethodCardProps) {
  return (
    <PanelCard title={title} subtitle={description}>
      <InlineStatus label={enabled ? 'Enabled' : 'Not configured'} tone={enabled ? 'success' : 'neutral'} />
      {children ? <div className="mt-4">{children}</div> : null}
    </PanelCard>
  )
}

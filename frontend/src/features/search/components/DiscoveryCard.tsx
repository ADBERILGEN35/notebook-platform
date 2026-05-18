import type { ReactNode } from 'react'

type DiscoveryCardProps = {
  title: string
  description?: string
  children?: ReactNode
  actions?: ReactNode
}

export function DiscoveryCard({ title, description, children, actions }: DiscoveryCardProps) {
  return (
    <article className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-card">
      <header className="mb-3 flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="font-display text-headline-sm text-on-surface">{title}</h3>
          {description ? <p className="mt-1 text-body-md text-on-surface-variant">{description}</p> : null}
        </div>
        {actions}
      </header>
      {children}
    </article>
  )
}

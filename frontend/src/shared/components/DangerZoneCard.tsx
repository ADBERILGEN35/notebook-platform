import type { ReactNode } from 'react'

type DangerZoneCardProps = {
  title: string
  description: string
  actions: ReactNode
}

export function DangerZoneCard({ title, description, actions }: DangerZoneCardProps) {
  return (
    <section
      className="rounded-xl border border-error/30 bg-error-container/20 p-4 sm:p-5"
      aria-labelledby="danger-zone-title"
    >
      <h2 id="danger-zone-title" className="font-display text-headline-sm text-error">
        {title}
      </h2>
      <p className="mt-2 text-body-md text-on-surface-variant">{description}</p>
      <div className="mt-4 flex flex-wrap gap-2">{actions}</div>
    </section>
  )
}

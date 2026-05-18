import type { PropsWithChildren, ReactNode } from 'react'

type SectionCardProps = PropsWithChildren<{
  title: string
  description?: string
  actions?: ReactNode
  className?: string
}>

export function SectionCard({ title, description, actions, className = '', children }: SectionCardProps) {
  const headingId = `section-${title.replace(/\s+/g, '-').toLowerCase()}`
  return (
    <section
      className={`rounded-xl border border-outline-variant bg-surface-container-lowest p-4 sm:p-5 ${className}`}
      aria-labelledby={headingId}
    >
      <header className="mb-4 flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 id={headingId} className="font-display text-headline-sm text-on-surface">
            {title}
          </h2>
          {description ? <p className="mt-1 text-body-md text-on-surface-variant">{description}</p> : null}
        </div>
        {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
      </header>
      {children}
    </section>
  )
}

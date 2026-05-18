import type { PropsWithChildren, ReactNode } from 'react'

type PageSectionProps = PropsWithChildren<{
  title?: string
  description?: string
  actions?: ReactNode
  className?: string
}>

export function PageSection({ title, description, actions, className = '', children }: PageSectionProps) {
  return (
    <section className={`space-y-4 ${className}`} aria-labelledby={title ? 'section-title' : undefined}>
      {title || actions ? (
        <header className="flex flex-wrap items-end justify-between gap-3">
          <div>
            {title ? (
              <h2 id="section-title" className="font-display text-headline-sm text-on-surface">
                {title}
              </h2>
            ) : null}
            {description ? <p className="mt-1 text-body-md text-on-surface-variant">{description}</p> : null}
          </div>
          {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
        </header>
      ) : null}
      {children}
    </section>
  )
}

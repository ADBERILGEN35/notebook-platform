import type { PropsWithChildren, ReactNode } from 'react'

type PanelCardProps = PropsWithChildren<{
  title?: string
  subtitle?: string
  footer?: ReactNode
  className?: string
}>

export function PanelCard({ title, subtitle, footer, className = '', children }: PanelCardProps) {
  return (
    <article className={`flex flex-col rounded-xl border border-outline-variant bg-surface-container-low shadow-card ${className}`}>
      {title ? (
        <header className="border-b border-outline-variant px-4 py-3">
          <h3 className="font-display text-headline-sm text-on-surface">{title}</h3>
          {subtitle ? <p className="mt-0.5 text-label-md text-on-surface-variant">{subtitle}</p> : null}
        </header>
      ) : null}
      <div className="flex-1 p-4">{children}</div>
      {footer ? <footer className="border-t border-outline-variant px-4 py-3">{footer}</footer> : null}
    </article>
  )
}

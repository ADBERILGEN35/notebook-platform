import type { ReactNode } from 'react'

type EmptyStateProps = {
  title: string
  message: string
  icon?: ReactNode
  actions?: ReactNode
}

export function EmptyState({ title, message, icon, actions }: EmptyStateProps) {
  return (
    <section
      className="rounded-xl border border-dashed border-outline-variant bg-surface-container-lowest p-8 text-center"
      aria-label={title}
    >
      {icon ? <div className="mb-4 flex justify-center text-primary">{icon}</div> : null}
      <h3 className="font-display text-headline-sm text-on-surface">{title}</h3>
      <p className="mt-2 text-body-md text-on-surface-variant">{message}</p>
      {actions ? <div className="mt-6 flex flex-wrap justify-center gap-2">{actions}</div> : null}
    </section>
  )
}

import type { PropsWithChildren, ReactNode } from 'react'

type SettingsSectionProps = PropsWithChildren<{
  title: string
  description?: string
  actions?: ReactNode
}>

export function SettingsSection({ title, description, actions, children }: SettingsSectionProps) {
  return (
    <section className="space-y-4">
      <header className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h2 className="font-display text-headline-sm text-on-surface">{title}</h2>
          {description ? <p className="mt-1 text-body-md text-on-surface-variant">{description}</p> : null}
        </div>
        {actions}
      </header>
      <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 sm:p-5">{children}</div>
    </section>
  )
}

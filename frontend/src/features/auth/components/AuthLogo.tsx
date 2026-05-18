import type { ReactNode } from 'react'
import { BookIcon, ShieldIcon } from './AuthIcons'

type AuthLogoProps = {
  title?: string
  subtitle?: string
  icon?: 'book' | 'shield'
  footer?: ReactNode
}

export function AuthLogo({
  title = 'Notebook Platform',
  subtitle,
  icon = 'book',
  footer,
}: AuthLogoProps) {
  const Icon = icon === 'shield' ? ShieldIcon : BookIcon
  return (
    <>
      <div className="mb-6 flex flex-col items-center text-center">
        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-primary text-white shadow-auth-card">
          <Icon className="h-6 w-6" />
        </div>
        <h1 className="font-display text-headline-md text-on-surface">{title}</h1>
        {subtitle ? <p className="mt-2 text-body-md text-on-surface-variant">{subtitle}</p> : null}
      </div>
      {footer}
    </>
  )
}

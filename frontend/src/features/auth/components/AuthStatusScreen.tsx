import type { ReactNode } from 'react'
import { AuthCard } from './AuthCard'
import { AuthLogo } from './AuthLogo'
import { LockIcon, VerifiedIcon } from './AuthIcons'

export type AuthStatusVariant = 'loading' | 'success' | 'error'

type AuthStatusScreenProps = {
  variant: AuthStatusVariant
  title: string
  message: string
  actions?: ReactNode
}

export function AuthStatusScreen({ variant, title, message, actions }: AuthStatusScreenProps) {
  return (
  <>
    <AuthLogo icon="book" subtitle="Enterprise Workspace" />
    <AuthCard>
      <div className="flex flex-col items-center text-center">
        {variant === 'loading' ? (
          <div className="relative mb-6">
            <span
              className="inline-block h-12 w-12 animate-spin rounded-full border-[3px] border-transparent border-t-primary"
              role="status"
              aria-label="Loading"
            />
            <LockIcon className="absolute inset-0 m-auto h-4 w-4 text-primary/40" />
          </div>
        ) : variant === 'success' ? (
          <div className="mb-6 flex h-12 w-12 items-center justify-center rounded-full bg-primary-container text-white">
            <VerifiedIcon className="h-6 w-6" />
          </div>
        ) : (
          <div className="mb-6 flex h-12 w-12 items-center justify-center rounded-full bg-error-container text-error">
            <span className="text-headline-sm font-semibold" aria-hidden>
              !
            </span>
          </div>
        )}
        <h2 className="font-display text-headline-sm text-on-surface">{title}</h2>
        <p className="mt-2 max-w-sm text-body-md text-on-surface-variant">{message}</p>
        {variant === 'loading' ? (
          <div className="mt-6 flex items-center gap-2 rounded-full bg-surface-container-low px-4 py-2">
            <VerifiedIcon className="h-4 w-4 text-primary" />
            <span className="text-label-md text-secondary">Secure SSO authentication</span>
          </div>
        ) : null}
        {actions ? <div className="mt-6 w-full space-y-3">{actions}</div> : null}
      </div>
    </AuthCard>
  </>
  )
}

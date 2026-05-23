import type { ReactNode } from 'react'
import { readableErrorMessage } from '../api/api-client'

type ErrorStateProps = {
  title?: string
  error?: unknown
  message?: string
  actions?: ReactNode
  className?: string
}

export function ErrorState({ title = 'Something went wrong', error, message, actions, className = '' }: ErrorStateProps) {
  const detail =
    message != null && message !== '' ? message : error != null ? readableErrorMessage(error) : undefined

  return (
    <section
      className={`rounded-xl border border-error-container bg-error-container/30 p-6 ${className}`}
      role="alert"
      aria-live="assertive"
    >
      <h2 className="font-display text-headline-sm text-error">{title}</h2>
      {detail ? <p className="mt-2 text-body-md text-on-surface-variant">{detail}</p> : null}
      {actions ? <div className="mt-4 flex flex-wrap gap-2">{actions}</div> : null}
    </section>
  )
}

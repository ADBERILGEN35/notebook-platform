import type { PropsWithChildren } from 'react'

export function Card({ children, className = '' }: PropsWithChildren<{ className?: string }>) {
  return (
    <div className={`rounded-lg border border-slate-200 bg-white p-4 shadow-card ${className}`}>{children}</div>
  )
}


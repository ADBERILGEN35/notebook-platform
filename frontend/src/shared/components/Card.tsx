import type { HTMLAttributes, PropsWithChildren } from 'react'

export function Card({
  children,
  className = '',
  ...rest
}: PropsWithChildren<{ className?: string }> & HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={`rounded-lg border border-slate-200 bg-white p-4 shadow-card ${className}`} {...rest}>
      {children}
    </div>
  )
}

import type { PropsWithChildren, ReactNode } from 'react'

type AuthCardProps = PropsWithChildren<{
  header?: ReactNode
  footer?: ReactNode
  className?: string
}>

export function AuthCard({ children, header, footer, className = '' }: AuthCardProps) {
  return (
    <section
      className={`overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-auth-card ${className}`}
    >
      {header ? <header className="border-b border-outline-variant/30 p-6 text-center">{header}</header> : null}
      <div className="p-6">{children}</div>
      {footer ? (
        <footer className="border-t border-outline-variant/30 bg-surface-container-low/30 p-6">{footer}</footer>
      ) : null}
    </section>
  )
}

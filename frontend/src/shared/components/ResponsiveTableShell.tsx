import type { PropsWithChildren } from 'react'

type ResponsiveTableShellProps = PropsWithChildren<{
  label: string
  testId?: string
  className?: string
}>

/** Horizontal scroll on narrow viewports; keeps dense admin tables usable on mobile. */
export function ResponsiveTableShell({ label, testId, className = '', children }: ResponsiveTableShellProps) {
  return (
    <div
      className={`min-w-0 -mx-1 overflow-x-auto sm:mx-0 ${className}`}
      role="region"
      aria-label={label}
      data-testid={testId}
    >
      {children}
    </div>
  )
}

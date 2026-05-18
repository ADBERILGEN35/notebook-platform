import type { PropsWithChildren } from 'react'

type ResponsiveContentProps = PropsWithChildren<{
  className?: string
  maxWidth?: 'md' | 'lg' | 'xl' | 'full'
}>

const maxWidthClass = {
  md: 'max-w-3xl',
  lg: 'max-w-5xl',
  xl: 'max-w-6xl',
  full: 'max-w-full',
}

export function ResponsiveContent({ children, className = '', maxWidth = 'xl' }: ResponsiveContentProps) {
  return (
    <article className={`mx-auto w-full ${maxWidthClass[maxWidth]} ${className}`}>{children}</article>
  )
}

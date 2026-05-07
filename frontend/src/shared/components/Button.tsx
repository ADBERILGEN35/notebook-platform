import type { ButtonHTMLAttributes, PropsWithChildren } from 'react'

export function Button({
  children,
  className = '',
  ...props
}: PropsWithChildren<ButtonHTMLAttributes<HTMLButtonElement>>) {
  return (
    <button
      {...props}
      className={`rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-800 shadow-card hover:bg-slate-50 disabled:opacity-60 ${className}`}
    >
      {children}
    </button>
  )
}


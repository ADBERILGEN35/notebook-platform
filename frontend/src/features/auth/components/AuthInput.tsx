import type { InputHTMLAttributes, ReactNode } from 'react'

type AuthInputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string
  hint?: ReactNode
  leadingIcon?: ReactNode
  trailing?: ReactNode
}

export function AuthInput({ label, hint, leadingIcon, trailing, id, className = '', ...props }: AuthInputProps) {
  const inputId = id ?? props.name ?? label.replace(/\s+/g, '-').toLowerCase()
  return (
    <div className="space-y-2">
      {hint && !leadingIcon ? (
        <div className="flex items-center justify-between">
          <label htmlFor={inputId} className="text-label-md font-medium text-on-surface">
            {label}
          </label>
          {hint}
        </div>
      ) : (
        <label htmlFor={inputId} className="block text-label-md font-medium text-on-surface-variant">
          {label}
        </label>
      )}
      <div className="relative">
        {leadingIcon ? (
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-outline">{leadingIcon}</span>
        ) : null}
        <input
          id={inputId}
          {...props}
          className={`w-full rounded-lg border border-outline-variant bg-surface-container-lowest py-2.5 text-body-md text-on-surface transition-all placeholder:text-outline/60 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary-fixed-dim ${
            leadingIcon ? 'pl-11 pr-3' : 'px-3'
          } ${trailing ? 'pr-10' : ''} ${className}`}
        />
        {trailing ? <span className="absolute right-3 top-1/2 -translate-y-1/2">{trailing}</span> : null}
      </div>
    </div>
  )
}

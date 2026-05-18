import type { ReactNode } from 'react'

type InfoRowProps = {
  label: string
  value: ReactNode
  hint?: string
}

export function InfoRow({ label, value, hint }: InfoRowProps) {
  return (
    <div className="flex flex-col gap-1 border-b border-outline-variant py-3 last:border-b-0 sm:flex-row sm:items-center sm:justify-between">
      <span className="text-label-md font-medium text-on-surface-variant">{label}</span>
      <span className="text-body-md text-on-surface">
        {value}
        {hint ? <span className="mt-1 block text-label-md text-on-surface-variant">{hint}</span> : null}
      </span>
    </div>
  )
}

import type { ReactNode } from 'react'

type QuickActionCardProps = {
  title: string
  description: string
  icon?: ReactNode
  onClick?: () => void
  href?: string
  disabled?: boolean
}

export function QuickActionCard({ title, description, icon, onClick, disabled }: QuickActionCardProps) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="flex w-full items-start gap-3 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 text-left shadow-card transition hover:border-primary/30 hover:bg-surface-container-low focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60"
    >
      {icon ? (
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary-fixed text-primary">
          {icon}
        </span>
      ) : null}
      <span>
        <span className="block font-display text-headline-sm text-on-surface">{title}</span>
        <span className="mt-1 block text-body-md text-on-surface-variant">{description}</span>
      </span>
    </button>
  )
}

type StatusBadgeProps = {
  label: string
  tone?: 'neutral' | 'success' | 'warning' | 'error'
}

const toneClass: Record<NonNullable<StatusBadgeProps['tone']>, string> = {
  neutral: 'bg-surface-container text-on-surface-variant border-outline-variant',
  success: 'bg-primary-fixed text-primary border-primary-fixed-dim',
  warning: 'bg-amber-50 text-amber-900 border-amber-200',
  error: 'bg-error-container text-error border-error-container',
}

export function StatusBadge({ label, tone = 'neutral' }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-label-md font-medium ${toneClass[tone]}`}
    >
      {label}
    </span>
  )
}

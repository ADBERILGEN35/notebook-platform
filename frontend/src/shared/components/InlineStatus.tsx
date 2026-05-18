type InlineStatusTone = 'neutral' | 'success' | 'warning' | 'error'

const toneClass: Record<InlineStatusTone, string> = {
  neutral: 'bg-surface-container-high text-on-surface-variant',
  success: 'bg-emerald-50 text-emerald-800',
  warning: 'bg-amber-50 text-amber-900',
  error: 'bg-error-container/40 text-error',
}

type InlineStatusProps = {
  label: string
  tone?: InlineStatusTone
}

export function InlineStatus({ label, tone = 'neutral' }: InlineStatusProps) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-label-md font-medium ${toneClass[tone]}`}>
      {label}
    </span>
  )
}

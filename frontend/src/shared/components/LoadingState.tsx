type LoadingStateProps = {
  label?: string
}

export function LoadingState(props?: LoadingStateProps) {
  const { label } = props ?? {}
  return (
    <div className="flex items-center gap-3 py-8" role="status" aria-live="polite" aria-busy="true">
      <span className="inline-block h-5 w-5 animate-spin rounded-full border-2 border-outline-variant border-t-primary" />
      <span className="text-body-md text-on-surface-variant">{label ?? 'Loading…'}</span>
    </div>
  )
}

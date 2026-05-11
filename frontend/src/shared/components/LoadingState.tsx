type LoadingStateProps = {
  /** Shown instead of the default "Loading..." text. */
  label?: string
}

export function LoadingState(props?: LoadingStateProps) {
  const { label } = props ?? {}
  return <div className="text-sm text-slate-500">{label ?? 'Loading...'}</div>
}


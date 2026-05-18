type PreferenceToggleProps = {
  label: string
  description?: string
  checked: boolean
  disabled?: boolean
  locked?: boolean
  onChange: (checked: boolean) => void
}

export function PreferenceToggle({ label, description, checked, disabled, locked, onChange }: PreferenceToggleProps) {
  return (
    <label className="flex items-start justify-between gap-3 rounded-lg border border-outline-variant px-3 py-2">
      <span className="min-w-0">
        <span className="block text-body-md font-medium text-on-surface">{label}</span>
        {description ? <span className="mt-0.5 block text-label-md text-on-surface-variant">{description}</span> : null}
        {locked ? <span className="mt-1 block text-label-md text-amber-800">Locked by workspace policy</span> : null}
      </span>
      <input
        type="checkbox"
        className="mt-1 h-4 w-4 shrink-0"
        checked={checked}
        disabled={disabled || locked}
        onChange={(e) => onChange(e.target.checked)}
        aria-label={label}
      />
    </label>
  )
}

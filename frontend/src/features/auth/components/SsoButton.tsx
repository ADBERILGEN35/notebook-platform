import { KeyIcon } from './AuthIcons'
import { AuthButton } from './AuthButton'

type SsoButtonProps = {
  label?: string
  disabled?: boolean
  onClick?: () => void
}

export function SsoButton({ label = 'Continue with SSO', disabled, onClick }: SsoButtonProps) {
  return (
    <AuthButton variant="secondary" disabled={disabled} onClick={onClick} type="button">
      <KeyIcon className="h-5 w-5 shrink-0" />
      {label}
    </AuthButton>
  )
}

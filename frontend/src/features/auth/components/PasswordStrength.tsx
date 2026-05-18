import { CheckCircleIcon } from './AuthIcons'
import { evaluatePasswordStrength } from '../password-strength'

type PasswordStrengthProps = {
  password: string
}

export function PasswordStrength({ password }: PasswordStrengthProps) {
  const { score, rules } = evaluatePasswordStrength(password)
  if (!password) return null

  return (
    <div className="space-y-2 pt-1" aria-live="polite">
      <div className="flex gap-1">
        {[0, 1, 2, 3].map((index) => (
          <span
            key={index}
            className={`h-1 flex-1 rounded-full ${index < score ? 'bg-primary' : 'bg-outline-variant'}`}
          />
        ))}
      </div>
      <ul className="flex flex-wrap gap-x-4 gap-y-1">
        {rules.map((rule) => (
          <li
            key={rule.id}
            className={`flex items-center gap-1 text-label-md ${rule.met ? 'text-primary' : 'text-on-surface-variant'}`}
          >
            <CheckCircleIcon filled={rule.met} className="h-3.5 w-3.5 shrink-0" />
            {rule.label}
          </li>
        ))}
      </ul>
    </div>
  )
}

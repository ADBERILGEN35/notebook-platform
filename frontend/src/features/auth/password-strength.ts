export type PasswordRule = {
  id: string
  label: string
  met: boolean
}

export type PasswordStrengthResult = {
  score: number
  rules: PasswordRule[]
}

export function evaluatePasswordStrength(password: string): PasswordStrengthResult {
  const rules: PasswordRule[] = [
    { id: 'length', label: '10+ characters', met: password.length >= 10 },
    { id: 'lower', label: '1 lowercase letter', met: /[a-z]/.test(password) },
    { id: 'upper', label: '1 uppercase letter', met: /[A-Z]/.test(password) },
    { id: 'digit', label: '1 number', met: /\d/.test(password) },
    { id: 'symbol', label: '1 symbol', met: /[^A-Za-z0-9]/.test(password) },
  ]
  const metCount = rules.filter((r) => r.met).length
  const score = password.length === 0 ? 0 : Math.min(4, Math.ceil((metCount / rules.length) * 4))
  return { score, rules }
}

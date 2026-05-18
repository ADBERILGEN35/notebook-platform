import { describe, expect, it } from 'vitest'
import { evaluatePasswordStrength } from './password-strength'

describe('evaluatePasswordStrength', () => {
  it('marks rules for a strong password', () => {
    const { score, rules } = evaluatePasswordStrength('Str0ng!Pass')
    expect(score).toBeGreaterThanOrEqual(3)
    expect(rules.find((r) => r.id === 'length')?.met).toBe(true)
    expect(rules.find((r) => r.id === 'symbol')?.met).toBe(true)
  })

  it('returns zero score for empty password', () => {
    expect(evaluatePasswordStrength('').score).toBe(0)
  })
})

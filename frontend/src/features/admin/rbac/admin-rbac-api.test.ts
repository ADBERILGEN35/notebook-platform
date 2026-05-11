import { describe, expect, it } from 'vitest'
import { formatOverrideChecksumShort } from './admin-rbac-api'

describe('formatOverrideChecksumShort', () => {
  it('returns em dash for empty', () => {
    expect(formatOverrideChecksumShort('')).toBe('—')
    expect(formatOverrideChecksumShort(null)).toBe('—')
  })

  it('truncates long sha256 hex', () => {
    const full =
      'sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789'
    expect(formatOverrideChecksumShort(full)).toMatch(/^sha256:abcd…6789$/)
  })
})

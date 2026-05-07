import { describe, expect, it } from 'vitest'
import { loginSchema, signupSchema } from './auth-api'

describe('auth schemas', () => {
  it('validates login payload', () => {
    expect(loginSchema.safeParse({ email: 'x@example.com', password: 'secret' }).success).toBe(true)
    expect(loginSchema.safeParse({ email: 'bad', password: '' }).success).toBe(false)
  })

  it('validates signup payload', () => {
    expect(
      signupSchema.safeParse({
        name: 'Jane',
        email: 'jane@example.com',
        password: '1234567890',
        avatarUrl: '',
      }).success
    ).toBe(true)
    expect(
      signupSchema.safeParse({
        name: 'Jane',
        email: 'jane@example.com',
        password: 'short',
        avatarUrl: '',
      }).success
    ).toBe(false)
  })
})


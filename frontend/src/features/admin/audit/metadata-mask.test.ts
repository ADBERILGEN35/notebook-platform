import { describe, expect, it } from 'vitest'
import { maskSensitiveMetadata } from './metadata-mask'

describe('metadata mask', () => {
  it('masks sensitive map keys', () => {
    expect(
      maskSensitiveMetadata({
        ok: 'x',
        apiKey: 'secret-value',
        nested: { bearerToken: 't1' },
      }),
    ).toEqual({
      ok: 'x',
      apiKey: '***masked***',
      nested: { bearerToken: '***masked***' },
    })
  })
})

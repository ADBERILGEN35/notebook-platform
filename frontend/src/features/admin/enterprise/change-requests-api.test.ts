import { describe, expect, it } from 'vitest'
import { ADMIN_CHANGE_REQUEST_OPERATIONS } from './change-requests-api'

describe('change-requests-api', () => {
  it('exposes allow-listed operations aligned with gateway contract', () => {
    const types = ADMIN_CHANGE_REQUEST_OPERATIONS.map((o) => o.type).sort()
    expect(types).toEqual(
      [
        'ADMIN_MFA_MODE_UPDATE',
        'MERGE_ANALYSIS_ROLLOUT_REQUEST',
        'MERGE_APPLY_ROLLOUT_REQUEST',
        'SCIM_BULK_ROLLOUT_REQUEST',
      ].sort(),
    )
  })
})

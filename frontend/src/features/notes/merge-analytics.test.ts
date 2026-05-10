import { describe, expect, it } from 'vitest'
import { trackMergeEvent } from './merge-analytics'

describe('merge analytics', () => {
  it('no-op adapter does not throw', () => {
    expect(() =>
      trackMergeEvent({
        source: 'online',
        backendAnalyzeUsed: true,
        backendApplyUsed: false,
        action: 'dialog_opened',
        hasSafeSuggestion: false,
        conflictCount: 0,
      }),
    ).not.toThrow()
  })
})

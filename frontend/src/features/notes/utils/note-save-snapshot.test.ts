import { describe, expect, it } from 'vitest'
import {
  areSnapshotsEqual,
  createNoteSaveSnapshot,
} from './note-save-snapshot'

describe('note save snapshot', () => {
  it('creates stable snapshot payload', () => {
    const snapshot = createNoteSaveSnapshot('Title', [])
    expect(snapshot.serialized).toContain('Title')
  })

  it('compares snapshots by serialized payload', () => {
    const a = createNoteSaveSnapshot('A', [])
    const b = createNoteSaveSnapshot('A', [])
    const c = createNoteSaveSnapshot('B', [])
    expect(areSnapshotsEqual(a, b)).toBe(true)
    expect(areSnapshotsEqual(a, c)).toBe(false)
  })
})


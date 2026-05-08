import { describe, expect, it } from 'vitest'
import type { NoteBlock } from '../../../shared/types/api'
import { createNoteSaveSnapshot } from './note-save-snapshot'
import {
  analyzeNoteConflict,
  buildMergeSuggestion,
  detectConflicts,
  canAutoMerge,
} from './blocknote-merge'

const paragraph = (id: string, props: Record<string, unknown> = {}): NoteBlock => ({
  id,
  type: 'paragraph',
  content: [],
  props,
  children: [],
})

const snap = (title: string, blocks: NoteBlock[]) => createNoteSaveSnapshot(title, blocks)

describe('blocknote-merge', () => {
  it('merges title local only with remote content change (Rule A)', () => {
    const base = snap('A', [paragraph('1', { v: 1 })])
    const local = snap('B', [paragraph('1', { v: 1 })])
    const remote = snap('A', [paragraph('1', { v: 2 })])
    const s = buildMergeSuggestion(base, local, remote)
    expect(s).not.toBeNull()
    expect(s!.title).toBe('B')
    expect(s!.contentBlocks[0].props).toEqual({ v: 2 })
  })

  it('merges disjoint edits on different blocks', () => {
    const base = snap('T', [paragraph('a', { x: 1 }), paragraph('b', { y: 1 })])
    const local = snap('T', [paragraph('a', { x: 2 }), paragraph('b', { y: 1 })])
    const remote = snap('T', [paragraph('a', { x: 1 }), paragraph('b', { y: 2 })])
    const s = buildMergeSuggestion(base, local, remote)
    expect(s).not.toBeNull()
    expect(s!.contentBlocks.find((b) => b.id === 'a')?.props).toEqual({ x: 2 })
    expect(s!.contentBlocks.find((b) => b.id === 'b')?.props).toEqual({ y: 2 })
  })

  it('conflicts when same block edited both sides', () => {
    const base = snap('T', [paragraph('1', { v: 0 })])
    const local = snap('T', [paragraph('1', { v: 1 })])
    const remote = snap('T', [paragraph('1', { v: 2 })])
    expect(detectConflicts(base, local, remote).some((c) => c.reason === 'same_block_divergent')).toBe(true)
    expect(buildMergeSuggestion(base, local, remote)).toBeNull()
  })

  it('conflicts on delete vs edit', () => {
    const base = snap('T', [paragraph('a'), paragraph('b')])
    const local = snap('T', [paragraph('a')])
    const remote = snap('T', [paragraph('a'), paragraph('b', { edited: true })])
    expect(detectConflicts(base, local, remote).some((c) => c.reason === 'delete_vs_edit')).toBe(true)
  })

  it('conflicts on unknown block type', () => {
    const base = snap('T', [{ id: 'x', type: 'paragraph', content: [], props: {}, children: [] }])
    const local = snap('T', [{ id: 'x', type: 'weirdCustom', content: [], props: {}, children: [] }])
    const remote = snap('T', [{ id: 'x', type: 'paragraph', content: [], props: {}, children: [] }])
    expect(detectConflicts(base, local, remote).some((c) => c.reason === 'unknown_block_type')).toBe(true)
  })

  it('conflicts when title diverges on both sides', () => {
    const b = [paragraph('1')]
    const base = snap('Base', b)
    const local = snap('Local', b)
    const remote = snap('Remote', b)
    expect(detectConflicts(base, local, remote).some((c) => c.reason === 'title_divergent')).toBe(true)
  })

  it('analyzeNoteConflict exposes summaries', () => {
    const base = snap('A', [paragraph('1')])
    const local = snap('A', [paragraph('1', { k: 1 })])
    const remote = snap('A', [paragraph('1', { k: 2 })])
    const a = analyzeNoteConflict(base, local, remote)
    expect(a.localChangeSummary.length).toBeGreaterThan(0)
    expect(a.remoteChangeSummary.length).toBeGreaterThan(0)
    expect(a.conflictSummaries.length).toBeGreaterThan(0)
    expect(canAutoMerge(a)).toBe(false)
  })

  it('canAutoMerge true when suggestion exists', () => {
    const base = snap('A', [paragraph('1')])
    const local = snap('B', [paragraph('1')])
    const remote = snap('A', [paragraph('1', { r: 1 })])
    const a = analyzeNoteConflict(base, local, remote)
    expect(canAutoMerge(a)).toBe(true)
  })
})

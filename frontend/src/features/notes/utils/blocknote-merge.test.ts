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

describe('blocknote-merge basics', () => {
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

describe('blocknote-merge move/reorder rules (Faz 95)', () => {
  it('safe: local reorders root, remote edits unrelated block', () => {
    const base = snap('T', [paragraph('a', { v: 1 }), paragraph('b', { v: 1 }), paragraph('c', { v: 1 })])
    const local = snap('T', [paragraph('b', { v: 1 }), paragraph('a', { v: 1 }), paragraph('c', { v: 1 })])
    const remote = snap('T', [paragraph('a', { v: 1 }), paragraph('b', { v: 1 }), paragraph('c', { v: 2 })])
    const a = analyzeNoteConflict(base, local, remote)
    expect(a.conflicts).toHaveLength(0)
    expect(a.reorderedBlocks.length).toBeGreaterThan(0)
    expect(canAutoMerge(a)).toBe(true)
    const ids = a.suggestion!.contentBlocks.map((x) => x.id)
    expect(ids).toEqual(['b', 'a', 'c'])
    expect(a.suggestion!.contentBlocks.find((x) => x.id === 'c')?.props).toEqual({ v: 2 })
  })

  it('safe: local reorders root, remote appends new block', () => {
    const base = snap('T', [paragraph('a'), paragraph('b')])
    const local = snap('T', [paragraph('b'), paragraph('a')])
    const remote = snap('T', [paragraph('a'), paragraph('b'), paragraph('c')])
    const a = analyzeNoteConflict(base, local, remote)
    expect(a.conflicts).toHaveLength(0)
    expect(canAutoMerge(a)).toBe(true)
    const ids = a.suggestion!.contentBlocks.map((x) => x.id)
    expect(ids).toEqual(['b', 'a', 'c'])
  })

  it('conflict: both sides move the same block to different positions', () => {
    const base = snap('T', [paragraph('a'), paragraph('b'), paragraph('c')])
    const local = snap('T', [paragraph('b'), paragraph('a'), paragraph('c')])
    const remote = snap('T', [paragraph('a'), paragraph('c'), paragraph('b')])
    const a = analyzeNoteConflict(base, local, remote)
    expect(a.conflicts.some((c) => c.reason === 'block_move_conflict')).toBe(true)
    expect(a.moveConflicts.length).toBeGreaterThan(0)
    expect(canAutoMerge(a)).toBe(false)
  })

  it('conflict: local moves a block the server edited', () => {
    const base = snap('T', [paragraph('a', { v: 1 }), paragraph('b')])
    const local = snap('T', [paragraph('b'), paragraph('a', { v: 1 })])
    const remote = snap('T', [paragraph('a', { v: 2 }), paragraph('b')])
    const a = analyzeNoteConflict(base, local, remote)
    expect(a.conflicts.some((c) => c.reason === 'block_moved_and_edited')).toBe(true)
    expect(canAutoMerge(a)).toBe(false)
  })

  it('conflict: local moves a block the server deleted', () => {
    const base = snap('T', [paragraph('a'), paragraph('b')])
    const local = snap('T', [paragraph('b'), paragraph('a')])
    const remote = snap('T', [paragraph('b')])
    const a = analyzeNoteConflict(base, local, remote)
    expect(a.conflicts.some((c) => c.reason === 'block_deleted_after_move')).toBe(true)
  })

  it('conflict: cross-parent move requires manual review (Faz 95 scope)', () => {
    const childA = paragraph('child', { v: 1 })
    const base = snap('T', [
      { id: 'p1', type: 'paragraph', content: [], props: {}, children: [childA] },
      { id: 'p2', type: 'paragraph', content: [], props: {}, children: [] },
    ])
    const local = snap('T', [
      { id: 'p1', type: 'paragraph', content: [], props: {}, children: [] },
      { id: 'p2', type: 'paragraph', content: [], props: {}, children: [childA] },
    ])
    const remote = snap('T', [
      { id: 'p1', type: 'paragraph', content: [], props: {}, children: [childA] },
      { id: 'p2', type: 'paragraph', content: [], props: {}, children: [] },
    ])
    const a = analyzeNoteConflict(base, local, remote)
    expect(a.conflicts.some((c) => c.reason === 'block_cross_parent_unsupported')).toBe(true)
    expect(canAutoMerge(a)).toBe(false)
  })

  it('same-target move on both sides is not a conflict', () => {
    const base = snap('T', [paragraph('a'), paragraph('b'), paragraph('c')])
    const local = snap('T', [paragraph('b'), paragraph('a'), paragraph('c')])
    const remote = snap('T', [paragraph('b'), paragraph('a'), paragraph('c')])
    const a = analyzeNoteConflict(base, local, remote)
    expect(a.conflicts).toHaveLength(0)
    expect(canAutoMerge(a)).toBe(true)
  })

  it('safe-reorder summary lists moved/reordered entries with ids and indices', () => {
    const base = snap('T', [paragraph('a'), paragraph('b'), paragraph('c')])
    const local = snap('T', [paragraph('c'), paragraph('a'), paragraph('b')])
    const remote = snap('T', [paragraph('a'), paragraph('b'), paragraph('c')])
    const a = analyzeNoteConflict(base, local, remote)
    expect(a.reorderedBlocks.length).toBeGreaterThanOrEqual(2)
    const c = a.reorderedBlocks.find((m) => m.blockId === 'c')
    expect(c).toBeDefined()
    expect(c!.fromIndex).toBe(2)
    expect(c!.toIndex).toBe(0)
  })
})

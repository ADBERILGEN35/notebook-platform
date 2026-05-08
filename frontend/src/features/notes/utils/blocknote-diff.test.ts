import { describe, expect, it } from 'vitest'
import type { NoteBlock } from '../../../shared/types/api'
import { diffBlocks, findDuplicateBlockIds, flattenWithParent, summarizeDiff } from './blocknote-diff'

const paragraph = (id: string, extra: Partial<NoteBlock> = {}): NoteBlock => ({
  id,
  type: 'paragraph',
  content: [],
  props: {},
  children: [],
  ...extra,
})

describe('blocknote-diff', () => {
  it('detects added and removed blocks', () => {
    const base = [paragraph('a'), paragraph('b')]
    const target = [paragraph('a'), paragraph('c')]
    const d = diffBlocks(base, target)
    expect(d.some((x) => x.blockId === 'c' && x.changeTypes.includes('added'))).toBe(true)
    expect(d.some((x) => x.blockId === 'b' && x.changeTypes.includes('removed'))).toBe(true)
  })

  it('detects content change on same id', () => {
    const base = [paragraph('a', { content: [{ type: 'text', text: 'hi', styles: {} }] })]
    const target = [paragraph('a', { content: [{ type: 'text', text: 'bye', styles: {} }] })]
    const d = diffBlocks(base, target)
    expect(d.some((x) => x.changeTypes.includes('content_changed'))).toBe(true)
  })

  it('finds duplicate ids', () => {
    const blocks = [paragraph('dup'), paragraph('dup')]
    expect(findDuplicateBlockIds(blocks).length).toBeGreaterThan(0)
  })

  it('flattenWithParent preserves order index', () => {
    const blocks = [paragraph('p'), { ...paragraph('c'), children: [paragraph('n')] as NoteBlock[] }]
    const flat = flattenWithParent(blocks)
    expect(flat.find((e) => e.block.id === 'n')?.parentId).toBe('c')
  })

  it('summarizeDiff produces readable lines', () => {
    const d = diffBlocks([paragraph('a')], [paragraph('b')])
    const lines = summarizeDiff(d)
    expect(lines.some((l) => l.includes('added') || l.includes('removed'))).toBe(true)
  })
})

import { describe, expect, it } from 'vitest'
import {
  createEmptyDocument,
  extractPlainTextFromBlocks,
  fromBlockNoteDocument,
  toBlockNoteDocument,
} from './blocknote-serialization'

describe('blocknote serialization', () => {
  it('creates empty document', () => {
    const doc = createEmptyDocument()
    expect(doc).toHaveLength(1)
    expect(doc[0].type).toBe('paragraph')
  })

  it('hydrates existing blocks', () => {
    const blocks = [
      {
        id: 'b1',
        type: 'paragraph',
        content: [{ type: 'text', text: 'hello', styles: {} }],
        props: {},
        children: [],
      },
    ]
    const parsed = toBlockNoteDocument(blocks)
    expect(parsed).toHaveLength(1)
  })

  it('falls back for invalid payload', () => {
    const parsed = toBlockNoteDocument({ invalid: true })
    expect(parsed).toHaveLength(1)
  })

  it('serializes editor document to backend block shape', () => {
    const output = fromBlockNoteDocument([
      {
        id: 'b2',
        type: 'paragraph',
        content: [{ type: 'text', text: 'world', styles: {} }],
        props: {},
        children: [],
      } as never,
    ])
    expect(output[0].type).toBe('paragraph')
    expect(output[0].id).toBe('b2')
  })

  it('extracts plain text for preview or dirty check', () => {
    const text = extractPlainTextFromBlocks([
      {
        id: 'b3',
        type: 'paragraph',
        content: [{ type: 'text', text: 'alpha', styles: {} }],
        props: {},
        children: [],
      },
    ])
    expect(text).toContain('alpha')
  })
})


import type { NoteBlock } from '../types/api'

export const createParagraphBlock = (): NoteBlock => ({
  id: crypto.randomUUID(),
  type: 'paragraph',
  content: [],
  props: {},
  children: [],
})

export const textToBlocks = (text: string): NoteBlock[] => {
  const trimmed = text.trim()
  if (!trimmed) return [createParagraphBlock()]
  return [
    {
      id: crypto.randomUUID(),
      type: 'paragraph',
      content: [{ type: 'text', text: trimmed, styles: {} }],
      props: {},
      children: [],
    },
  ]
}


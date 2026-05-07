import type { Block } from '@blocknote/core'
import type { NoteBlock } from '../../../shared/types/api'

const isObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null

const toArray = (value: unknown): unknown[] => (Array.isArray(value) ? value : [])

const normalizeBlock = (block: unknown): NoteBlock | null => {
  if (!isObject(block)) return null
  const id = typeof block.id === 'string' && block.id.trim() ? block.id : crypto.randomUUID()
  const type = typeof block.type === 'string' && block.type.trim() ? block.type : 'paragraph'
  const props = isObject(block.props) ? block.props : {}
  const content = toArray(block.content)
  const childrenRaw = toArray(block.children)
  const children = childrenRaw.map(normalizeBlock).filter(Boolean) as NoteBlock[]
  return { id, type, props, content, children }
}

export const createEmptyDocument = (): NoteBlock[] => [
  {
    id: crypto.randomUUID(),
    type: 'paragraph',
    content: [],
    props: {},
    children: [],
  },
]

export const toBlockNoteDocument = (contentBlocks: unknown): Partial<Block>[] => {
  if (!Array.isArray(contentBlocks)) return createEmptyDocument() as unknown as Partial<Block>[]
  const normalized = contentBlocks.map(normalizeBlock).filter(Boolean) as NoteBlock[]
  if (!normalized.length) return createEmptyDocument() as unknown as Partial<Block>[]
  return normalized as unknown as Partial<Block>[]
}

export const fromBlockNoteDocument = (document: Block[]): NoteBlock[] => {
  if (!Array.isArray(document) || !document.length) return createEmptyDocument()
  const normalized = document.map(normalizeBlock).filter(Boolean) as NoteBlock[]
  return normalized.length ? normalized : createEmptyDocument()
}

export const extractPlainTextFromBlocks = (blocks: unknown): string => {
  const normalized = toBlockNoteDocument(blocks) as unknown as NoteBlock[]
  const values: string[] = []
  const collect = (blockItems: NoteBlock[]) => {
    for (const item of blockItems) {
      for (const node of item.content) {
        if (isObject(node) && typeof node.text === 'string') {
          values.push(node.text)
        }
      }
      if (item.children.length) collect(item.children as unknown as NoteBlock[])
    }
  }
  collect(normalized)
  return values.join(' ').trim()
}


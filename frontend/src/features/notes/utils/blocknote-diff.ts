import type { NoteBlock } from '../../../shared/types/api'

export type BlockChangeType =
  | 'added'
  | 'removed'
  | 'content_changed'
  | 'props_changed'
  | 'children_changed'
  | 'moved'
  | 'type_changed'

export type BlockDiff = {
  blockId: string
  blockType: string
  changeTypes: BlockChangeType[]
}

type FlatEntry = {
  block: NoteBlock
  parentId: string | null
  index: number
}

export const blockSignature = (block: NoteBlock): string =>
  JSON.stringify({
    type: block.type,
    props: block.props,
    content: block.content,
    childIds: (block.children as NoteBlock[]).map((c) => c?.id).filter(Boolean),
  })

export function flattenWithParent(blocks: NoteBlock[], parentId: string | null = null): FlatEntry[] {
  const out: FlatEntry[] = []
  for (let i = 0; i < blocks.length; i += 1) {
    const block = blocks[i]
    out.push({ block, parentId, index: i })
    const kids = block.children as NoteBlock[]
    if (Array.isArray(kids) && kids.length) {
      out.push(...flattenWithParent(kids, block.id))
    }
  }
  return out
}

export function findDuplicateBlockIds(blocks: NoteBlock[]): string[] {
  const seen = new Set<string>()
  const dups: string[] = []
  for (const { block } of flattenWithParent(blocks)) {
    if (seen.has(block.id)) dups.push(block.id)
    else seen.add(block.id)
  }
  return dups
}

export function diffBlocks(base: NoteBlock[], target: NoteBlock[]): BlockDiff[] {
  const baseFlat = flattenWithParent(base)
  const targetFlat = flattenWithParent(target)
  const baseById = new Map(baseFlat.map((e) => [e.block.id, e]))
  const targetById = new Map(targetFlat.map((e) => [e.block.id, e]))
  const ids = new Set<string>([...baseById.keys(), ...targetById.keys()])
  const diffs: BlockDiff[] = []

  for (const id of ids) {
    const b = baseById.get(id)
    const t = targetById.get(id)
    if (!b && t) {
      diffs.push({ blockId: id, blockType: t.block.type, changeTypes: ['added'] })
      continue
    }
    if (b && !t) {
      diffs.push({ blockId: id, blockType: b.block.type, changeTypes: ['removed'] })
      continue
    }
    if (!b || !t) continue

    const changeTypes: BlockChangeType[] = []
    if (b.block.type !== t.block.type) changeTypes.push('type_changed')
    if (blockSignature(b.block) !== blockSignature(t.block)) {
      if (b.block.type !== t.block.type) {
        /* already flagged */
      } else {
        const sigProps = JSON.stringify(b.block.props) !== JSON.stringify(t.block.props)
        const sigContent = JSON.stringify(b.block.content) !== JSON.stringify(t.block.content)
        const sigChildIds =
          JSON.stringify((b.block.children as NoteBlock[]).map((c) => c.id)) !==
          JSON.stringify((t.block.children as NoteBlock[]).map((c) => c.id))
        if (sigProps) changeTypes.push('props_changed')
        if (sigContent) changeTypes.push('content_changed')
        if (sigChildIds) changeTypes.push('children_changed')
      }
    }
    if (b.parentId !== t.parentId || b.index !== t.index) {
      changeTypes.push('moved')
    }
    if (changeTypes.length) {
      diffs.push({ blockId: id, blockType: t.block.type, changeTypes })
    }
  }

  return diffs
}

export function summarizeDiff(diffs: BlockDiff[]): string[] {
  const lines: string[] = []
  for (const d of diffs) {
    const parts = d.changeTypes.join(', ')
    lines.push(`Block ${d.blockId} (${d.blockType}): ${parts}`)
  }
  return lines.length ? lines : ['No block-level changes']
}

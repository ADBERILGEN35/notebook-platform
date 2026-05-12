import type { NoteBlock } from '../../../shared/types/api'
import type { NoteSaveSnapshot } from './note-save-snapshot'
import {
  blockSignature,
  diffBlocks,
  findDuplicateBlockIds,
  flattenWithParent,
  summarizeDiff,
} from './blocknote-diff'

/** BlockNote core types we allow for client-side suggested merge (conservative). */
export const MERGE_ALLOWED_BLOCK_TYPES = new Set([
  'paragraph',
  'heading',
  'bulletListItem',
  'numberedListItem',
  'checkListItem',
  'quote',
  'codeBlock',
  'toggleListItem',
  'image',
  'video',
  'audio',
  'file',
  'table',
])

export type MergeConflictReason =
  | 'same_block_divergent'
  | 'delete_vs_edit'
  | 'unknown_block_type'
  | 'missing_block_id'
  | 'duplicate_block_id'
  | 'title_divergent'
  | 'block_move_conflict'
  | 'block_moved_and_edited'
  | 'block_deleted_after_move'
  | 'block_cross_parent_unsupported'
  /** Legacy v1 catch-all kept for backward compat with older backend payloads. */
  | 'move_or_structure'

export type MergeChangeType =
  | 'block_moved'
  | 'block_reordered'
  | 'block_parent_changed'
  | 'block_index_changed'
  | 'block_moved_and_edited'

export type MergeConflict = {
  reason: MergeConflictReason
  blockId?: string
  message: string
}

export type MergeMoveSummary = {
  blockId: string
  blockType: string
  fromIndex: number | null
  toIndex: number | null
  parentChanged: boolean
}

export type MergeSuggestion = {
  title: string
  contentBlocks: NoteBlock[]
}

export type MergeAnalysis = {
  suggestion: MergeSuggestion | null
  conflicts: MergeConflict[]
  localChangeSummary: string[]
  remoteChangeSummary: string[]
  conflictSummaries: string[]
  movedBlocks: MergeMoveSummary[]
  reorderedBlocks: MergeMoveSummary[]
  moveConflicts: MergeMoveSummary[]
}

type IndexedBlock = {
  block: NoteBlock
  parentId: string | null
  index: number
  bodySignature: string
}

type BlockIndex = {
  byId: Map<string, IndexedBlock>
  childrenByParent: Map<string, string[]>
  duplicateIds: string[]
  hasMissingId: boolean
}

type MoveStatus = 'none' | 'reordered' | 'parent_changed'

function deepCloneBlocks(blocks: NoteBlock[]): NoteBlock[] {
  return JSON.parse(JSON.stringify(blocks)) as NoteBlock[]
}

function hasMissingBlockId(blocks: NoteBlock[]): boolean {
  for (const { block } of flattenWithParent(blocks)) {
    if (typeof block.id !== 'string' || !block.id.trim()) return true
  }
  return false
}

function hasUnknownBlockType(blocks: NoteBlock[]): string | null {
  for (const { block } of flattenWithParent(blocks)) {
    if (!MERGE_ALLOWED_BLOCK_TYPES.has(block.type)) return block.type
  }
  return null
}

function bodySignature(block: NoteBlock): string {
  const children = (block.children as NoteBlock[]) ?? []
  const childIdsSorted = children
    .map((c) => c?.id)
    .filter((id): id is string => typeof id === 'string')
    .slice()
    .sort()
  return JSON.stringify({
    type: block.type,
    props: block.props,
    content: block.content,
    childIdsSorted,
  })
}

function indexBlocks(blocks: NoteBlock[]): BlockIndex {
  const byId = new Map<string, IndexedBlock>()
  const childrenByParent = new Map<string, string[]>()
  const duplicateIds: string[] = []
  let hasMissingId = false
  const walk = (list: NoteBlock[], parentId: string | null) => {
    const ids: string[] = []
    for (let i = 0; i < list.length; i += 1) {
      const block = list[i]
      const id = block.id
      if (typeof id !== 'string' || !id.trim()) {
        hasMissingId = true
        continue
      }
      if (byId.has(id)) duplicateIds.push(id)
      byId.set(id, { block, parentId, index: i, bodySignature: bodySignature(block) })
      ids.push(id)
      const kids = block.children as NoteBlock[]
      if (Array.isArray(kids) && kids.length) walk(kids, id)
    }
    childrenByParent.set(parentId ?? '', ids)
  }
  walk(blocks, null)
  return { byId, childrenByParent, duplicateIds, hasMissingId }
}

function computeMove(base: BlockIndex, target: BlockIndex, id: string): MoveStatus {
  const b = base.byId.get(id)
  const t = target.byId.get(id)
  if (!b || !t) return 'none'
  if (b.parentId !== t.parentId) return 'parent_changed'
  if (b.index !== t.index) return 'reordered'
  return 'none'
}

function replaceBlockBodyInTree(blocks: NoteBlock[], id: string, replacement: NoteBlock): boolean {
  for (let i = 0; i < blocks.length; i += 1) {
    if (blocks[i].id === id) {
      blocks[i] = deepCloneBlocks([replacement])[0]
      return true
    }
    const kids = blocks[i].children as NoteBlock[]
    if (Array.isArray(kids) && kids.length && replaceBlockBodyInTree(kids, id, replacement)) {
      return true
    }
  }
  return false
}

function removeBlockFromTree(blocks: NoteBlock[], id: string): boolean {
  for (let i = 0; i < blocks.length; i += 1) {
    if (blocks[i].id === id) {
      blocks.splice(i, 1)
      return true
    }
    const kids = blocks[i].children as NoteBlock[]
    if (Array.isArray(kids) && kids.length && removeBlockFromTree(kids, id)) return true
  }
  return false
}

function findChildrenArray(blocks: NoteBlock[], parentId: string): NoteBlock[] | null {
  for (const block of blocks) {
    if (block.id === parentId) {
      return (block.children as NoteBlock[]) ?? null
    }
    const kids = block.children as NoteBlock[]
    if (Array.isArray(kids) && kids.length) {
      const nested = findChildrenArray(kids, parentId)
      if (nested) return nested
    }
  }
  return null
}

function reorderSiblings(siblings: NoteBlock[], desiredOrder: string[]): boolean {
  const byId = new Map<string, NoteBlock>()
  for (const child of siblings) {
    if (typeof child.id === 'string') byId.set(child.id, child)
  }
  const placed = new Set<string>()
  const reordered: NoteBlock[] = []
  for (const id of desiredOrder) {
    const node = byId.get(id)
    if (!node) continue
    reordered.push(node)
    placed.add(id)
  }
  for (const [id, node] of byId) {
    if (!placed.has(id)) reordered.push(node)
  }
  siblings.length = 0
  for (const node of reordered) siblings.push(node)
  return true
}

function describeMove(id: string, base: BlockIndex, target: BlockIndex, status: MoveStatus): string {
  const t = target.byId.get(id)
  const label = t?.block.type ?? 'block'
  if (status === 'parent_changed') {
    return `Moved ${label} block ${id} to a different parent`
  }
  const from = base.byId.get(id)?.index ?? null
  const to = t?.index ?? null
  return `Reordered ${label} block ${id} from position ${from} to ${to}`
}

function describeMoveServer(id: string, base: BlockIndex, target: BlockIndex, status: MoveStatus): string {
  const t = target.byId.get(id)
  const label = t?.block.type ?? 'block'
  if (status === 'parent_changed') {
    return `Server moved ${label} block ${id} to a different parent`
  }
  const from = base.byId.get(id)?.index ?? null
  const to = t?.index ?? null
  return `Server reordered ${label} block ${id} from position ${from} to ${to}`
}

function summaryEntry(id: string, base: BlockIndex, target: BlockIndex, status: MoveStatus): MergeMoveSummary {
  const tEntry = target.byId.get(id)
  return {
    blockId: id,
    blockType: tEntry?.block.type ?? 'block',
    fromIndex: base.byId.get(id)?.index ?? null,
    toIndex: tEntry?.index ?? null,
    parentChanged: status === 'parent_changed',
  }
}

export function analyzeNoteConflict(
  base: NoteSaveSnapshot,
  local: NoteSaveSnapshot,
  remote: NoteSaveSnapshot,
): MergeAnalysis {
  const conflicts: MergeConflict[] = []
  const localChangeSummary: string[] = []
  const remoteChangeSummary: string[] = []
  const movedBlocks: MergeMoveSummary[] = []
  const reorderedBlocks: MergeMoveSummary[] = []
  const moveConflicts: MergeMoveSummary[] = []
  const safeLocalMoves = new Map<string, MoveStatus>()

  for (const [labelKey, snapshot] of [
    ['base', base],
    ['local', local],
    ['remote', remote],
  ] as const) {
    const blocks = snapshot.contentBlocks
    const dups = findDuplicateBlockIds(blocks)
    if (dups.length) {
      conflicts.push({
        reason: 'duplicate_block_id',
        blockId: dups[0],
        message: `Duplicate block id(s) in ${labelKey} version.`,
      })
    }
    if (hasMissingBlockId(blocks)) {
      conflicts.push({
        reason: 'missing_block_id',
        message: `A block is missing a stable id in ${labelKey} version.`,
      })
    }
    const unknown = hasUnknownBlockType(blocks)
    if (unknown) {
      conflicts.push({
        reason: 'unknown_block_type',
        message: `Unsupported block type for merge: ${unknown}`,
      })
    }
  }

  const titleBase = base.title.trim()
  const titleLocal = local.title.trim()
  const titleRemote = remote.title.trim()
  if (titleLocal !== titleBase && titleRemote !== titleBase && titleLocal !== titleRemote) {
    conflicts.push({ reason: 'title_divergent', message: 'Title changed in both versions differently.' })
  }
  if (titleLocal !== titleBase) localChangeSummary.push('Title changed')
  if (titleRemote !== titleBase) remoteChangeSummary.push('Title changed on server')

  const baseIdx = indexBlocks(base.contentBlocks)
  const localIdx = indexBlocks(local.contentBlocks)
  const remoteIdx = indexBlocks(remote.contentBlocks)

  // Hard-stop on structural id problems: do not emit per-block analysis.
  if (
    baseIdx.hasMissingId ||
    localIdx.hasMissingId ||
    baseIdx.duplicateIds.length ||
    localIdx.duplicateIds.length ||
    remoteIdx.duplicateIds.length ||
    conflicts.some((c) => c.reason === 'unknown_block_type')
  ) {
    return finalize(conflicts, localChangeSummary, remoteChangeSummary, movedBlocks, reorderedBlocks, moveConflicts, null)
  }

  const allIds = new Set<string>([
    ...baseIdx.byId.keys(),
    ...localIdx.byId.keys(),
    ...remoteIdx.byId.keys(),
  ])

  for (const id of allIds) {
    const inBase = baseIdx.byId.has(id)
    const inLocal = localIdx.byId.has(id)
    const inRemote = remoteIdx.byId.has(id)

    if (!inBase && inLocal && !inRemote) {
      localChangeSummary.push(`Added block ${id}`)
      continue
    }
    if (!inBase && !inLocal && inRemote) {
      remoteChangeSummary.push(`Server added block ${id}`)
      continue
    }
    if (!inBase && inLocal && inRemote) {
      const lSig = localIdx.byId.get(id)?.bodySignature
      const rSig = remoteIdx.byId.get(id)?.bodySignature
      if (lSig !== rSig) {
        conflicts.push({
          reason: 'same_block_divergent',
          blockId: id,
          message: `Both versions added block ${id} differently.`,
        })
      }
      continue
    }

    const localDeleted = inBase && !inLocal
    const remoteDeleted = inBase && !inRemote
    const baseSig = baseIdx.byId.get(id)?.bodySignature
    const localSig = inLocal ? localIdx.byId.get(id)?.bodySignature : undefined
    const remoteSig = inRemote ? remoteIdx.byId.get(id)?.bodySignature : undefined
    const localEdited = inLocal && baseSig !== localSig
    const remoteEdited = inRemote && baseSig !== remoteSig
    const localMove = inLocal ? computeMove(baseIdx, localIdx, id) : 'none'
    const remoteMove = inRemote ? computeMove(baseIdx, remoteIdx, id) : 'none'

    if (localEdited) localChangeSummary.push(`Changed block ${id}`)
    if (remoteEdited) remoteChangeSummary.push(`Server changed block ${id}`)
    if (localMove !== 'none') {
      localChangeSummary.push(describeMove(id, baseIdx, localIdx, localMove))
      const entry = summaryEntry(id, baseIdx, localIdx, localMove)
      if (localMove === 'parent_changed') movedBlocks.push(entry)
      else reorderedBlocks.push(entry)
    }
    if (remoteMove !== 'none') {
      remoteChangeSummary.push(describeMoveServer(id, baseIdx, remoteIdx, remoteMove))
    }
    if (localDeleted) localChangeSummary.push(`Removed block ${id}`)
    if (remoteDeleted) remoteChangeSummary.push(`Server removed block ${id}`)

    if (localDeleted && remoteMove !== 'none') {
      conflicts.push({
        reason: 'block_deleted_after_move',
        blockId: id,
        message: `Block ${id} was moved on the server but removed locally.`,
      })
      continue
    }
    if (remoteDeleted && localMove !== 'none') {
      conflicts.push({
        reason: 'block_deleted_after_move',
        blockId: id,
        message: `Block ${id} was moved locally but removed on the server.`,
      })
      continue
    }
    if (localDeleted && remoteEdited) {
      conflicts.push({
        reason: 'delete_vs_edit',
        blockId: id,
        message: `Block ${id} was removed locally but edited on the server.`,
      })
      continue
    }
    if (remoteDeleted && localEdited) {
      conflicts.push({
        reason: 'delete_vs_edit',
        blockId: id,
        message: `Block ${id} was removed on the server but edited locally.`,
      })
      continue
    }
    if (localDeleted || remoteDeleted) continue

    if (localMove !== 'none' && remoteMove !== 'none') {
      const lEntry = localIdx.byId.get(id)
      const rEntry = remoteIdx.byId.get(id)
      const sameDestination =
        lEntry?.parentId === rEntry?.parentId && lEntry?.index === rEntry?.index
      if (!sameDestination) {
        const entry = summaryEntry(id, baseIdx, localIdx, localMove)
        moveConflicts.push(entry)
        conflicts.push({
          reason: 'block_move_conflict',
          blockId: id,
          message: `Both versions moved block ${id} to different positions.`,
        })
        continue
      }
      // Already aligned — remote applies the move; no local action needed.
    } else if (localMove === 'parent_changed' || remoteMove === 'parent_changed') {
      conflicts.push({
        reason: 'block_cross_parent_unsupported',
        blockId: id,
        message: `Cross-parent move on block ${id} requires manual review.`,
      })
      continue
    } else if (localMove !== 'none' && remoteEdited) {
      conflicts.push({
        reason: 'block_moved_and_edited',
        blockId: id,
        message: `Server edited block ${id} that you moved.`,
      })
      continue
    } else if (remoteMove !== 'none' && localEdited) {
      conflicts.push({
        reason: 'block_moved_and_edited',
        blockId: id,
        message: `You edited block ${id} that the server moved.`,
      })
      continue
    } else if (localEdited && remoteEdited && localSig !== remoteSig) {
      conflicts.push({
        reason: 'same_block_divergent',
        blockId: id,
        message: `Block ${id} was edited in both versions.`,
      })
      continue
    }

    if (localMove === 'reordered' && remoteMove === 'none') {
      safeLocalMoves.set(id, localMove)
    }
  }

  const suggestion = conflicts.length === 0 ? buildSuggestion(base, local, remote, baseIdx, localIdx, remoteIdx, safeLocalMoves) : null

  return finalize(conflicts, localChangeSummary, remoteChangeSummary, movedBlocks, reorderedBlocks, moveConflicts, suggestion)
}

function finalize(
  conflicts: MergeConflict[],
  localChangeSummary: string[],
  remoteChangeSummary: string[],
  movedBlocks: MergeMoveSummary[],
  reorderedBlocks: MergeMoveSummary[],
  moveConflicts: MergeMoveSummary[],
  suggestion: MergeSuggestion | null,
): MergeAnalysis {
  return {
    suggestion,
    conflicts,
    localChangeSummary,
    remoteChangeSummary,
    conflictSummaries: conflicts.map((c) => c.message),
    movedBlocks,
    reorderedBlocks,
    moveConflicts,
  }
}

function buildSuggestion(
  base: NoteSaveSnapshot,
  local: NoteSaveSnapshot,
  remote: NoteSaveSnapshot,
  baseIdx: BlockIndex,
  localIdx: BlockIndex,
  remoteIdx: BlockIndex,
  safeLocalMoves: Map<string, MoveStatus>,
): MergeSuggestion | null {
  const merged = deepCloneBlocks(remote.contentBlocks)

  for (const id of baseIdx.byId.keys()) {
    const inLocal = localIdx.byId.has(id)
    const inRemote = remoteIdx.byId.has(id)
    const baseSig = baseIdx.byId.get(id)?.bodySignature
    const localSig = localIdx.byId.get(id)?.bodySignature
    const remoteSig = remoteIdx.byId.get(id)?.bodySignature

    if (!inLocal && inRemote) {
      if (baseSig === remoteSig) {
        removeBlockFromTree(merged, id)
      }
      continue
    }
    if (!inLocal) continue
    const localChanged = baseSig !== localSig
    const remoteChanged = baseSig !== remoteSig
    if (localChanged && !remoteChanged) {
      const replacement = localIdx.byId.get(id)?.block
      if (replacement && !replaceBlockBodyInTree(merged, id, replacement)) return null
    }
  }

  // Local-only root additions (nested additions stay manual).
  const baseIds = new Set(baseIdx.byId.keys())
  const remoteIds = new Set(remoteIdx.byId.keys())
  for (const id of localIdx.byId.keys()) {
    if (baseIds.has(id) || remoteIds.has(id)) continue
    const entry = localIdx.byId.get(id)
    if (entry && entry.parentId === null) {
      merged.push(deepCloneBlocks([entry.block])[0])
    } else {
      return null
    }
  }

  // Apply safe local same-parent reorders.
  if (safeLocalMoves.size) {
    const parents = new Set<string>()
    for (const id of safeLocalMoves.keys()) {
      const parent = localIdx.byId.get(id)?.parentId
      if (parent === undefined) return null
      parents.add(parent ?? '')
    }
    for (const parent of parents) {
      const desiredOrder = localIdx.childrenByParent.get(parent) ?? []
      const siblings = parent === '' ? merged : findChildrenArray(merged, parent)
      if (!siblings) return null
      if (!reorderSiblings(siblings, desiredOrder)) return null
    }
  }

  let mergedTitle = remote.title
  const titleBase = base.title.trim()
  const titleLocal = local.title.trim()
  const titleRemote = remote.title.trim()
  if (titleLocal !== titleBase && titleRemote === titleBase) mergedTitle = local.title
  else if (titleRemote !== titleBase && titleLocal === titleBase) mergedTitle = remote.title
  else if (titleLocal === titleRemote) mergedTitle = local.title

  return { title: mergedTitle, contentBlocks: merged }
}

export function detectConflicts(
  base: NoteSaveSnapshot,
  local: NoteSaveSnapshot,
  remote: NoteSaveSnapshot,
): MergeConflict[] {
  return analyzeNoteConflict(base, local, remote).conflicts
}

export function buildMergeSuggestion(
  base: NoteSaveSnapshot,
  local: NoteSaveSnapshot,
  remote: NoteSaveSnapshot,
): MergeSuggestion | null {
  return analyzeNoteConflict(base, local, remote).suggestion
}

export function canAutoMerge(analysis: MergeAnalysis): boolean {
  return analysis.suggestion !== null
}

/** @deprecated kept for backward compat — callers should consult analyzeNoteConflict instead. */
export function hasStructuralMove(base: NoteBlock[], local: NoteBlock[], remote: NoteBlock[]): boolean {
  const dl = diffBlocks(base, local)
  const dr = diffBlocks(base, remote)
  return (
    dl.some((x) => x.changeTypes.includes('moved')) || dr.some((x) => x.changeTypes.includes('moved'))
  )
}

/** @deprecated kept for legacy callers that still read summarizeDiff lines directly. */
export const _legacySummarize = (blocks: NoteBlock[], target: NoteBlock[]): string[] =>
  summarizeDiff(diffBlocks(blocks, target))

/** Re-export so existing imports keep working. */
export { blockSignature }

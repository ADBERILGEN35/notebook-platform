import type { NoteBlock } from '../../../shared/types/api'
import type { NoteSaveSnapshot } from './note-save-snapshot'
import {
  blockSignature,
  diffBlocks,
  findDuplicateBlockIds,
  flattenWithParent,
  summarizeDiff,
} from './blocknote-diff'
import { createNoteSaveSnapshot } from './note-save-snapshot'

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
  | 'move_or_structure'

export type MergeConflict = {
  reason: MergeConflictReason
  blockId?: string
  message: string
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

function deepCloneBlocks(blocks: NoteBlock[]): NoteBlock[] {
  return JSON.parse(JSON.stringify(blocks)) as NoteBlock[]
}

function blockMapsEqual(a: NoteBlock[], b: NoteBlock[]): boolean {
  return createNoteSaveSnapshot('', a).serialized === createNoteSaveSnapshot('', b).serialized
}

function replaceBlockInTree(blocks: NoteBlock[], id: string, replacement: NoteBlock): boolean {
  for (let i = 0; i < blocks.length; i += 1) {
    if (blocks[i].id === id) {
      blocks[i] = deepCloneBlocks([replacement])[0]
      return true
    }
    const kids = blocks[i].children as NoteBlock[]
    if (kids?.length && replaceBlockInTree(kids, id, replacement)) return true
  }
  return false
}

function removedIds(from: NoteBlock[], to: NoteBlock[]): Set<string> {
  const toFlat = new Set(flattenWithParent(to).map((e) => e.block.id))
  const out = new Set<string>()
  for (const { block } of flattenWithParent(from)) {
    if (!toFlat.has(block.id)) out.add(block.id)
  }
  return out
}

function addedIds(from: NoteBlock[], to: NoteBlock[]): Set<string> {
  const fromFlat = new Set(flattenWithParent(from).map((e) => e.block.id))
  const out = new Set<string>()
  for (const { block } of flattenWithParent(to)) {
    if (!fromFlat.has(block.id)) out.add(block.id)
  }
  return out
}

function editedIdsBetween(a: NoteBlock[], b: NoteBlock[]): Set<string> {
  const mapA = new Map(flattenWithParent(a).map((e) => [e.block.id, e.block]))
  const mapB = new Map(flattenWithParent(b).map((e) => [e.block.id, e.block]))
  const out = new Set<string>()
  for (const id of mapA.keys()) {
    const ba = mapA.get(id)
    const bb = mapB.get(id)
    if (ba && bb && blockSignature(ba) !== blockSignature(bb)) out.add(id)
  }
  return out
}

export function hasStructuralMove(base: NoteBlock[], local: NoteBlock[], remote: NoteBlock[]): boolean {
  const dl = diffBlocks(base, local)
  const dr = diffBlocks(base, remote)
  return (
    dl.some((x) => x.changeTypes.includes('moved')) || dr.some((x) => x.changeTypes.includes('moved'))
  )
}

export function detectConflicts(
  base: NoteSaveSnapshot,
  local: NoteSaveSnapshot,
  remote: NoteSaveSnapshot,
): MergeConflict[] {
  const conflicts: MergeConflict[] = []

  for (const label of ['base', 'local', 'remote'] as const) {
    const blocks = label === 'base' ? base.contentBlocks : label === 'local' ? local.contentBlocks : remote.contentBlocks
    const dups = findDuplicateBlockIds(blocks)
    if (dups.length) {
      conflicts.push({
        reason: 'duplicate_block_id',
        blockId: dups[0],
        message: `Duplicate block id(s) in ${label} version.`,
      })
    }
    if (hasMissingBlockId(blocks)) {
      conflicts.push({
        reason: 'missing_block_id',
        message: `A block is missing a stable id in ${label} version.`,
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

  const localRemoved = removedIds(base.contentBlocks, local.contentBlocks)
  const remoteRemoved = removedIds(base.contentBlocks, remote.contentBlocks)
  const localEdited = editedIdsBetween(base.contentBlocks, local.contentBlocks)
  const remoteEdited = editedIdsBetween(base.contentBlocks, remote.contentBlocks)

  for (const id of localRemoved) {
    if (remoteEdited.has(id)) {
      conflicts.push({
        reason: 'delete_vs_edit',
        blockId: id,
        message: `Block ${id} was removed locally but edited on the server.`,
      })
    }
  }
  for (const id of remoteRemoved) {
    if (localEdited.has(id)) {
      conflicts.push({
        reason: 'delete_vs_edit',
        blockId: id,
        message: `Block ${id} was removed on the server but edited locally.`,
      })
    }
  }

  for (const id of localEdited) {
    if (remoteEdited.has(id)) {
      const lb = flattenWithParent(local.contentBlocks).find((e) => e.block.id === id)?.block
      const rb = flattenWithParent(remote.contentBlocks).find((e) => e.block.id === id)?.block
      if (lb && rb && blockSignature(lb) !== blockSignature(rb)) {
        conflicts.push({
          reason: 'same_block_divergent',
          blockId: id,
          message: `Block ${id} was edited in both versions.`,
        })
      }
    }
  }

  const titleLocal = local.title.trim()
  const titleRemote = remote.title.trim()
  const titleBase = base.title.trim()
  if (titleLocal !== titleBase && titleRemote !== titleBase && titleLocal !== titleRemote) {
    conflicts.push({
      reason: 'title_divergent',
      message: 'Title changed in both versions differently.',
    })
  }

  return conflicts
}

export function buildMergeSuggestion(
  base: NoteSaveSnapshot,
  local: NoteSaveSnapshot,
  remote: NoteSaveSnapshot,
): MergeSuggestion | null {
  if (detectConflicts(base, local, remote).length) return null
  if (hasStructuralMove(base.contentBlocks, local.contentBlocks, remote.contentBlocks)) return null

  const titleBase = base.title.trim()
  const titleLocal = local.title.trim()
  const titleRemote = remote.title.trim()

  if (
    blockMapsEqual(base.contentBlocks, local.contentBlocks) &&
    titleBase === titleLocal
  ) {
    return { title: remote.title, contentBlocks: deepCloneBlocks(remote.contentBlocks) }
  }

  if (
    blockMapsEqual(base.contentBlocks, remote.contentBlocks) &&
    titleBase === titleRemote
  ) {
    return { title: local.title, contentBlocks: deepCloneBlocks(local.contentBlocks) }
  }

  if (
    titleLocal !== titleBase &&
    titleRemote === titleBase &&
    blockMapsEqual(base.contentBlocks, local.contentBlocks) &&
    !blockMapsEqual(base.contentBlocks, remote.contentBlocks)
  ) {
    return { title: local.title, contentBlocks: deepCloneBlocks(remote.contentBlocks) }
  }

  const localEdited = editedIdsBetween(base.contentBlocks, local.contentBlocks)
  const remoteEdited = editedIdsBetween(base.contentBlocks, remote.contentBlocks)

  for (const id of localEdited) {
    if (remoteEdited.has(id)) return null
  }

  const localRemoved = removedIds(base.contentBlocks, local.contentBlocks)
  const remoteRemoved = removedIds(base.contentBlocks, remote.contentBlocks)
  for (const id of localRemoved) {
    if (remoteEdited.has(id)) return null
  }
  for (const id of remoteRemoved) {
    if (localEdited.has(id)) return null
  }

  const merged = deepCloneBlocks(remote.contentBlocks)
  const localById = new Map(flattenWithParent(local.contentBlocks).map((e) => [e.block.id, e.block]))

  for (const id of localEdited) {
    const replacement = localById.get(id)
    if (replacement && !replaceBlockInTree(merged, id, replacement)) return null
  }

  const mergedIds = new Set(flattenWithParent(merged).map((e) => e.block.id))
  for (const id of addedIds(base.contentBlocks, local.contentBlocks)) {
    if (mergedIds.has(id)) continue
    const entry = flattenWithParent(local.contentBlocks).find((e) => e.block.id === id)
    if (entry && entry.parentId === null) {
      merged.push(deepCloneBlocks([entry.block])[0])
      mergedIds.add(id)
    } else {
      return null
    }
  }

  for (const id of localRemoved) {
    const stillInRemote = flattenWithParent(remote.contentBlocks).some((e) => e.block.id === id)
    if (!stillInRemote) continue
    const remoteBlock = flattenWithParent(remote.contentBlocks).find((e) => e.block.id === id)?.block
    const baseBlock = flattenWithParent(base.contentBlocks).find((e) => e.block.id === id)?.block
    if (remoteBlock && baseBlock && blockSignature(remoteBlock) === blockSignature(baseBlock)) {
      if (!removeBlockFromTree(merged, id)) return null
    }
  }

  let mergedTitle = remote.title
  if (titleLocal !== titleBase && titleRemote === titleBase) mergedTitle = local.title
  else if (titleRemote !== titleBase && titleLocal === titleBase) mergedTitle = remote.title
  else if (titleLocal === titleRemote) mergedTitle = local.title

  return { title: mergedTitle, contentBlocks: merged }
}

function removeBlockFromTree(blocks: NoteBlock[], id: string): boolean {
  for (let i = 0; i < blocks.length; i += 1) {
    if (blocks[i].id === id) {
      blocks.splice(i, 1)
      return true
    }
    const kids = blocks[i].children as NoteBlock[]
    if (kids?.length && removeBlockFromTree(kids, id)) return true
  }
  return false
}

export function canAutoMerge(analysis: MergeAnalysis): boolean {
  return analysis.suggestion !== null
}

export function analyzeNoteConflict(
  base: NoteSaveSnapshot,
  local: NoteSaveSnapshot,
  remote: NoteSaveSnapshot,
): MergeAnalysis {
  const hardConflicts = detectConflicts(base, local, remote)
  const moved = hasStructuralMove(base.contentBlocks, local.contentBlocks, remote.contentBlocks)
  const moveConflicts: MergeConflict[] = moved
    ? [
        {
          reason: 'move_or_structure',
          message:
            'Block moves or structural reordering were detected. Suggested merge is not available.',
        },
      ]
    : []

  const conflicts = [...hardConflicts, ...moveConflicts]
  const suggestion =
    hardConflicts.length === 0 && !moved ? buildMergeSuggestion(base, local, remote) : null

  const localDiffs = diffBlocks(base.contentBlocks, local.contentBlocks)
  const remoteDiffs = diffBlocks(base.contentBlocks, remote.contentBlocks)

  return {
    suggestion,
    conflicts,
    localChangeSummary: summarizeDiff(localDiffs),
    remoteChangeSummary: summarizeDiff(remoteDiffs),
    conflictSummaries: conflicts.map((c) => c.message),
  }
}

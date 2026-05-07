import type { NoteBlock } from '../../../shared/types/api'

export type NoteSaveSnapshot = {
  title: string
  contentBlocks: NoteBlock[]
  serialized: string
}

const stableStringify = (value: unknown): string => JSON.stringify(value)

export const createNoteSaveSnapshot = (
  title: string,
  contentBlocks: NoteBlock[]
): NoteSaveSnapshot => ({
  title,
  contentBlocks,
  serialized: stableStringify({ title: title.trim(), contentBlocks }),
})

export const areSnapshotsEqual = (
  a: NoteSaveSnapshot | null,
  b: NoteSaveSnapshot | null
): boolean => {
  if (!a || !b) return false
  return a.serialized === b.serialized
}


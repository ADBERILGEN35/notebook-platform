import { BlockNoteView } from '@blocknote/mantine'
import { useCreateBlockNote } from '@blocknote/react'
import type { Block } from '@blocknote/core'
import '@blocknote/core/fonts/inter.css'
import '@blocknote/mantine/style.css'
import { useEffect } from 'react'
import { fromBlockNoteDocument, toBlockNoteDocument } from '../utils/blocknote-serialization'
import type { NoteBlock } from '../../../shared/types/api'

type Props = {
  initialContentBlocks: unknown
  onChange: (blocks: NoteBlock[]) => void
  readOnly?: boolean
  isSaving?: boolean
}

export function BlockNoteEditor({ initialContentBlocks, onChange, readOnly = false, isSaving = false }: Props) {
  const editor = useCreateBlockNote({
    initialContent: toBlockNoteDocument(initialContentBlocks),
  })

  useEffect(() => {
    const safeContent = toBlockNoteDocument(initialContentBlocks)
    editor.replaceBlocks(editor.document, safeContent)
  }, [editor, initialContentBlocks])

  return (
    <div
      data-testid="blocknote-editor"
      className={`min-h-[420px] rounded-md border border-slate-200 bg-white ${isSaving ? 'opacity-80' : ''}`}
    >
      <BlockNoteView
        editor={editor}
        editable={!readOnly}
        onChange={() => onChange(fromBlockNoteDocument(editor.document as Block[]))}
      />
    </div>
  )
}


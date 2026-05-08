import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { createEmptyDocument } from '../utils/blocknote-serialization'
import { createNoteSaveSnapshot } from '../utils/note-save-snapshot'
import { NoteConflictResolutionDialog } from './NoteConflictResolutionDialog'

vi.mock('../../../shared/hooks/useMediaQuery', () => ({
  useMediaQuery: () => false,
}))

describe('NoteConflictResolutionDialog', () => {
  const snapshot = createNoteSaveSnapshot('Local title', createEmptyDocument())

  it('renders conflict actions and previews', () => {
    render(
      <NoteConflictResolutionDialog
        open
        onClose={vi.fn()}
        localSnapshot={snapshot}
        serverTitle="Server title"
        serverPreview="Server preview"
        localPreview="Local preview"
        canSaveCopy
        onReloadLatest={vi.fn()}
        onSaveCopy={vi.fn()}
        onOverwrite={vi.fn()}
      />
    )

    expect(screen.getByTestId('conflict-dialog')).toBeTruthy()
    expect(screen.getByTestId('conflict-reload-latest')).toBeTruthy()
    expect(screen.getByTestId('conflict-save-copy')).toBeTruthy()
    expect(screen.getByTestId('conflict-overwrite')).toBeTruthy()
  })

  it('disables save-copy when notebook context is missing', () => {
    render(
      <NoteConflictResolutionDialog
        open
        onClose={vi.fn()}
        localSnapshot={snapshot}
        serverTitle="Server title"
        serverPreview="Server preview"
        localPreview="Local preview"
        canSaveCopy={false}
        onReloadLatest={vi.fn()}
        onSaveCopy={vi.fn()}
        onOverwrite={vi.fn()}
      />
    )

    expect(screen.getByTestId('conflict-save-copy')).toHaveProperty('disabled', true)
  })

  it('calls overwrite handler from destructive action', () => {
    const onOverwrite = vi.fn()
    render(
      <NoteConflictResolutionDialog
        open
        onClose={vi.fn()}
        localSnapshot={snapshot}
        serverTitle="Server title"
        serverPreview="Server preview"
        localPreview="Local preview"
        canSaveCopy
        onReloadLatest={vi.fn()}
        onSaveCopy={vi.fn()}
        onOverwrite={onOverwrite}
      />
    )
    fireEvent.click(screen.getByTestId('conflict-overwrite'))
    expect(onOverwrite).toHaveBeenCalled()
  })
})

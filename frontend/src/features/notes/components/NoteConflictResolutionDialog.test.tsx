import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { createEmptyDocument } from '../utils/blocknote-serialization'
import { createNoteSaveSnapshot } from '../utils/note-save-snapshot'
import { NoteConflictResolutionDialog } from './NoteConflictResolutionDialog'
import { analyzeNoteConflict } from '../utils/blocknote-merge'

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
        mergeAnalysis={null}
        remoteLoading={false}
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
        mergeAnalysis={null}
        remoteLoading={false}
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
        mergeAnalysis={null}
        remoteLoading={false}
        onReloadLatest={vi.fn()}
        onSaveCopy={vi.fn()}
        onOverwrite={onOverwrite}
      />
    )
    fireEvent.click(screen.getByTestId('conflict-overwrite'))
    expect(onOverwrite).toHaveBeenCalled()
  })

  it('shows diff summaries and apply merge when suggestion is safe', () => {
    const base = createNoteSaveSnapshot('A', [
      { id: '1', type: 'paragraph', content: [], props: {}, children: [] },
    ])
    const local = createNoteSaveSnapshot('B', [
      { id: '1', type: 'paragraph', content: [], props: {}, children: [] },
    ])
    const remote = createNoteSaveSnapshot('A', [
      { id: '1', type: 'paragraph', content: [], props: { r: 1 }, children: [] },
    ])
    const mergeAnalysis = analyzeNoteConflict(base, local, remote)
    const onMerge = vi.fn()
    render(
      <NoteConflictResolutionDialog
        open
        onClose={vi.fn()}
        localSnapshot={local}
        serverTitle="A"
        serverPreview="srv"
        localPreview="loc"
        canSaveCopy
        mergeAnalysis={mergeAnalysis}
        remoteLoading={false}
        onReloadLatest={vi.fn()}
        onSaveCopy={vi.fn()}
        onOverwrite={vi.fn()}
        onApplySuggestedMerge={onMerge}
      />,
    )
    expect(screen.getByTestId('conflict-local-diff-summary')).toBeTruthy()
    expect(screen.getByTestId('conflict-remote-diff-summary')).toBeTruthy()
    expect(screen.getByTestId('conflict-apply-merge')).toBeTruthy()
    fireEvent.click(screen.getByTestId('conflict-apply-merge'))
    expect(onMerge).toHaveBeenCalled()
  })

  it('hides apply merge for overlapping edits', () => {
    const base = createNoteSaveSnapshot('T', [
      { id: '1', type: 'paragraph', content: [], props: { v: 0 }, children: [] },
    ])
    const local = createNoteSaveSnapshot('T', [
      { id: '1', type: 'paragraph', content: [], props: { v: 1 }, children: [] },
    ])
    const remote = createNoteSaveSnapshot('T', [
      { id: '1', type: 'paragraph', content: [], props: { v: 2 }, children: [] },
    ])
    const mergeAnalysis = analyzeNoteConflict(base, local, remote)
    render(
      <NoteConflictResolutionDialog
        open
        onClose={vi.fn()}
        localSnapshot={local}
        serverTitle="T"
        serverPreview="srv"
        localPreview="loc"
        canSaveCopy
        mergeAnalysis={mergeAnalysis}
        remoteLoading={false}
        onReloadLatest={vi.fn()}
        onSaveCopy={vi.fn()}
        onOverwrite={vi.fn()}
        onApplySuggestedMerge={vi.fn()}
      />,
    )
    expect(screen.queryByTestId('conflict-apply-merge')).toBeNull()
    expect(screen.getByTestId('conflict-conflicts-summary')).toBeTruthy()
  })
})

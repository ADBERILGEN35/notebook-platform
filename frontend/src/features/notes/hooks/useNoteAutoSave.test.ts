import { act, renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Note } from '../../../shared/types/api'
import { createEmptyDocument } from '../utils/blocknote-serialization'
import { useNoteAutoSave } from './useNoteAutoSave'
import { ApiError } from '../../../shared/api/api-client'

const baseNote: Note = {
  id: 'n1',
  workspaceId: 'w1',
  notebookId: 'nb1',
  title: 'Initial',
  contentBlocks: createEmptyDocument(),
  contentSchemaVersion: 1,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
}

describe('useNoteAutoSave', () => {
  it('debounces save and marks saved on success', async () => {
    vi.useFakeTimers()
    const saveNote = vi.fn(async () => ({
      note: { ...baseNote, updatedAt: new Date().toISOString() },
      etag: '"note-rev-1"',
    }))

    const { result } = renderHook(() =>
      useNoteAutoSave({
        noteId: 'n1',
        title: 'Hello',
        contentBlocks: createEmptyDocument(),
        enabled: true,
        debounceMs: 1500,
        minChangeIntervalMs: 1000,
        baseUpdatedAt: baseNote.updatedAt,
        initialEtag: '"note-rev-0"',
        saveNote,
      })
    )

    act(() => result.current.scheduleSave())
    expect(result.current.saveState).toBe('scheduled')
    await act(async () => {
      vi.advanceTimersByTime(1500)
    })
    expect(saveNote).toHaveBeenCalledTimes(1)
    vi.useRealTimers()
  })

  it('manual save triggers immediately and clears scheduled timer', async () => {
    vi.useFakeTimers()
    const saveNote = vi.fn(async () => ({
      note: { ...baseNote, updatedAt: new Date().toISOString() },
      etag: '"note-rev-1"',
    }))
    const { result } = renderHook(() =>
      useNoteAutoSave({
        noteId: 'n1',
        title: 'Manual',
        contentBlocks: createEmptyDocument(),
        enabled: true,
        debounceMs: 1500,
        minChangeIntervalMs: 1000,
        baseUpdatedAt: baseNote.updatedAt,
        initialEtag: '"note-rev-0"',
        saveNote,
      })
    )

    act(() => result.current.scheduleSave())
    await act(async () => {
      await result.current.saveNow()
    })
    expect(saveNote).toHaveBeenCalledTimes(1)
    vi.useRealTimers()
  })

  it('transitions to conflict for 409/412 errors', async () => {
    const saveNote = vi.fn(async () => {
      throw new ApiError({
        timestamp: new Date().toISOString(),
        status: 412,
        errorCode: 'CONFLICT',
        message: 'Conflict',
        path: '/notes/n1',
      })
    })
    const { result } = renderHook(() =>
      useNoteAutoSave({
        noteId: 'n1',
        title: 'Conflict',
        contentBlocks: createEmptyDocument(),
        enabled: true,
        debounceMs: 10,
        minChangeIntervalMs: 0,
        baseUpdatedAt: baseNote.updatedAt,
        initialEtag: '"note-rev-0"',
        saveNote,
      })
    )

    await act(async () => {
      await result.current.saveNow()
    })
    expect(result.current.saveState).toBe('conflict')
    expect(result.current.conflictInfo?.localSnapshot.title).toBe('Conflict')
    expect(result.current.conflictInfo?.errorCode).toBe('CONFLICT')
  })

  it('supports retry after error', async () => {
    let attempts = 0
    const saveNote = vi.fn(async () => {
      attempts += 1
      if (attempts === 1) {
        throw new Error('boom')
      }
      return {
        note: { ...baseNote, updatedAt: new Date().toISOString() },
        etag: '"note-rev-2"',
      }
    })

    const { result } = renderHook(() =>
      useNoteAutoSave({
        noteId: 'n1',
        title: 'Retry',
        contentBlocks: createEmptyDocument(),
        enabled: true,
        debounceMs: 10,
        minChangeIntervalMs: 0,
        baseUpdatedAt: baseNote.updatedAt,
        initialEtag: '"note-rev-1"',
        saveNote,
      })
    )

    await act(async () => result.current.saveNow())
    expect(result.current.saveState).toBe('error')
    await act(async () => result.current.retry())
    expect(result.current.saveState).toBe('saved')
  })

  it('treats 428 as conflict state', async () => {
    const saveNote = vi.fn(async () => {
      throw new ApiError({
        timestamp: new Date().toISOString(),
        status: 428,
        errorCode: 'PRECONDITION_REQUIRED',
        message: 'If-Match required',
        path: '/notes/n1',
      })
    })
    const { result } = renderHook(() =>
      useNoteAutoSave({
        noteId: 'n1',
        title: 'Needs precondition',
        contentBlocks: createEmptyDocument(),
        enabled: true,
        debounceMs: 10,
        minChangeIntervalMs: 0,
        baseUpdatedAt: baseNote.updatedAt,
        initialEtag: null,
        saveNote,
      })
    )

    await act(async () => {
      await result.current.saveNow()
    })
    expect(result.current.saveState).toBe('conflict')
  })

  it('pauses autosave while conflict is unresolved', async () => {
    const saveNote = vi
      .fn()
      .mockRejectedValueOnce(
        new ApiError({
          timestamp: new Date().toISOString(),
          status: 412,
          errorCode: 'NOTE_CONFLICT',
          message: 'Conflict',
          path: '/notes/n1',
        })
      )
      .mockResolvedValue({
        note: { ...baseNote, updatedAt: new Date().toISOString() },
        etag: '"note-rev-2"',
      })

    const { result } = renderHook(() =>
      useNoteAutoSave({
        noteId: 'n1',
        title: 'Conflict paused',
        contentBlocks: createEmptyDocument(),
        enabled: true,
        debounceMs: 10,
        minChangeIntervalMs: 0,
        baseUpdatedAt: baseNote.updatedAt,
        initialEtag: '"note-rev-1"',
        saveNote,
      })
    )

    await act(async () => result.current.saveNow())
    expect(result.current.saveState).toBe('conflict')
    act(() => result.current.scheduleSave())
    expect(saveNote).toHaveBeenCalledTimes(1)
  })
})


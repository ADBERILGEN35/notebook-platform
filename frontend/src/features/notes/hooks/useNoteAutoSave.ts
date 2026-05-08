import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { Note, NoteBlock } from '../../../shared/types/api'
import { ApiError } from '../../../shared/api/api-client'
import {
  areSnapshotsEqual,
  createNoteSaveSnapshot,
  type NoteSaveSnapshot,
} from '../utils/note-save-snapshot'

export type AutoSaveState =
  | 'idle'
  | 'dirty'
  | 'scheduled'
  | 'saving'
  | 'saved'
  | 'error'
  | 'conflict'

type Params = {
  noteId: string
  title: string
  contentBlocks: NoteBlock[]
  enabled: boolean
  debounceMs: number
  minChangeIntervalMs: number
  baseUpdatedAt: string | null
  initialEtag: string | null
  saveNote: (payload: {
    title: string
    contentBlocks: NoteBlock[]
    ifMatch: string | null
  }) => Promise<{ note: Note; etag: string | null }>
}

export type NoteConflictInfo = {
  /** Last successfully synced client snapshot (common ancestor for three-way merge). */
  baseSnapshot: NoteSaveSnapshot
  localSnapshot: NoteSaveSnapshot
  failedPayload: { title: string; contentBlocks: NoteBlock[]; ifMatch: string | null }
  failedAt: string
  serverEtagAtFailure: string | null
  errorCode: string | null
  errorMessage: string
}

export function useNoteAutoSave({
  noteId,
  title,
  contentBlocks,
  enabled,
  debounceMs,
  minChangeIntervalMs,
  baseUpdatedAt,
  initialEtag,
  saveNote,
}: Params) {
  const [saveState, setSaveState] = useState<AutoSaveState>('idle')
  const [error, setError] = useState<unknown>(null)
  const [updatedAtBase, setUpdatedAtBase] = useState<string | null>(baseUpdatedAt)
  const [etag, setEtag] = useState<string | null>(initialEtag)
  const [isDirty, setIsDirty] = useState(false)
  const [conflictInfo, setConflictInfo] = useState<NoteConflictInfo | null>(null)

  const timerRef = useRef<number | null>(null)
  const inFlightRef = useRef(false)
  const queuedRef = useRef(false)
  const lastSavedRef = useRef<NoteSaveSnapshot | null>(null)
  const lastAttemptedRef = useRef<NoteSaveSnapshot | null>(null)
  const lastChangeTsRef = useRef(0)

  const currentSnapshot = useMemo(
    () => createNoteSaveSnapshot(title, contentBlocks),
    [title, contentBlocks]
  )

  const clearTimer = useCallback(() => {
    if (timerRef.current) {
      window.clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const executeSave = useCallback(async () => {
    clearTimer()
    if (!noteId) return
    if (inFlightRef.current) {
      queuedRef.current = true
      return
    }
    if (areSnapshotsEqual(lastSavedRef.current, currentSnapshot)) {
      setSaveState('saved')
      setIsDirty(false)
      return
    }

    inFlightRef.current = true
    setSaveState('saving')
    setError(null)
    lastAttemptedRef.current = currentSnapshot

    try {
      const saved = await saveNote({
        title: currentSnapshot.title,
        contentBlocks: currentSnapshot.contentBlocks,
        ifMatch: etag,
      })
      lastSavedRef.current = createNoteSaveSnapshot(saved.note.title, saved.note.contentBlocks)
      setUpdatedAtBase(saved.note.updatedAt)
      setEtag(saved.etag)
      setSaveState('saved')
      setIsDirty(false)
      setConflictInfo(null)
    } catch (err) {
      if (err instanceof ApiError && (err.status === 409 || err.status === 412 || err.status === 428)) {
        setSaveState('conflict')
        const baseSnapshot =
          lastSavedRef.current ??
          createNoteSaveSnapshot(currentSnapshot.title, currentSnapshot.contentBlocks)
        setConflictInfo({
          baseSnapshot,
          localSnapshot: currentSnapshot,
          failedPayload: {
            title: currentSnapshot.title,
            contentBlocks: currentSnapshot.contentBlocks,
            ifMatch: etag,
          },
          failedAt: new Date().toISOString(),
          serverEtagAtFailure: etag,
          errorCode: err.errorCode || null,
          errorMessage: err.message,
        })
      } else {
        setSaveState('error')
      }
      setError(err)
      setIsDirty(true)
    } finally {
      inFlightRef.current = false
      if (queuedRef.current) {
        queuedRef.current = false
        const now = Date.now()
        const delay = Math.max(0, minChangeIntervalMs - (now - lastChangeTsRef.current))
        timerRef.current = window.setTimeout(() => void executeSave(), delay)
        setSaveState('scheduled')
      }
    }
  }, [clearTimer, currentSnapshot, etag, minChangeIntervalMs, noteId, saveNote])

  const scheduleSave = useCallback(() => {
    setIsDirty(true)
    setError(null)
    if (saveState === 'conflict') {
      if (conflictInfo) {
        setConflictInfo({
          ...conflictInfo,
          localSnapshot: currentSnapshot,
        })
      }
      return
    }
    setSaveState('dirty')
    if (!enabled) return
    if (areSnapshotsEqual(lastSavedRef.current, currentSnapshot)) return

    clearTimer()
    lastChangeTsRef.current = Date.now()
    timerRef.current = window.setTimeout(() => void executeSave(), debounceMs)
    setSaveState('scheduled')
  }, [clearTimer, conflictInfo, currentSnapshot, debounceMs, enabled, executeSave, saveState])

  const saveNow = useCallback(async () => {
    clearTimer()
    await executeSave()
  }, [clearTimer, executeSave])

  const retry = useCallback(async () => {
    if (!lastAttemptedRef.current) return
    await saveNow()
  }, [saveNow])

  const resetWithServerNote = useCallback((note: Note) => {
    clearTimer()
    const snapshot = createNoteSaveSnapshot(note.title, note.contentBlocks)
    lastSavedRef.current = snapshot
    lastAttemptedRef.current = snapshot
    setUpdatedAtBase(note.updatedAt)
    setSaveState('saved')
    setError(null)
    setIsDirty(false)
  }, [clearTimer])

  const resetWithServerVersion = useCallback((note: Note, newEtag: string | null) => {
    clearTimer()
    const snapshot = createNoteSaveSnapshot(note.title, note.contentBlocks)
    lastSavedRef.current = snapshot
    lastAttemptedRef.current = snapshot
    setUpdatedAtBase(note.updatedAt)
    setEtag(newEtag)
    setSaveState('saved')
    setError(null)
    setIsDirty(false)
    setConflictInfo(null)
  }, [clearTimer])

  const markConflict = useCallback(() => {
    setSaveState('conflict')
    setIsDirty(true)
  }, [])

  const clearConflict = useCallback(() => {
    setConflictInfo(null)
    setError(null)
    if (isDirty) {
      setSaveState('dirty')
    } else {
      setSaveState('saved')
    }
  }, [isDirty])

  /**
   * Applies a client-built merge and PATCHes with the latest server ETag (post-conflict fetch).
   */
  const saveMergedAfterConflict = useCallback(
    async (merged: NoteSaveSnapshot, remoteBaseline: NoteSaveSnapshot, latestEtag: string | null) => {
      clearTimer()
      setError(null)
      inFlightRef.current = true
      setSaveState('saving')
      try {
        const saved = await saveNote({
          title: merged.title,
          contentBlocks: merged.contentBlocks,
          ifMatch: latestEtag,
        })
        lastSavedRef.current = createNoteSaveSnapshot(saved.note.title, saved.note.contentBlocks)
        lastAttemptedRef.current = lastSavedRef.current
        setUpdatedAtBase(saved.note.updatedAt)
        setEtag(saved.etag)
        setSaveState('saved')
        setIsDirty(false)
        setConflictInfo(null)
      } catch (err) {
        setError(err)
        setIsDirty(true)
        if (err instanceof ApiError && (err.status === 409 || err.status === 412 || err.status === 428)) {
          setSaveState('conflict')
          setConflictInfo({
            baseSnapshot: remoteBaseline,
            localSnapshot: merged,
            failedPayload: {
              title: merged.title,
              contentBlocks: merged.contentBlocks,
              ifMatch: latestEtag,
            },
            failedAt: new Date().toISOString(),
            serverEtagAtFailure: latestEtag,
            errorCode: err.errorCode || null,
            errorMessage: err.message,
          })
        } else {
          setSaveState('error')
        }
      } finally {
        inFlightRef.current = false
      }
    },
    [clearTimer, saveNote],
  )

  useEffect(() => () => clearTimer(), [clearTimer])

  useEffect(() => {
    if (!baseUpdatedAt || !updatedAtBase) return
    if (isDirty && baseUpdatedAt !== updatedAtBase) {
      markConflict()
    }
  }, [baseUpdatedAt, isDirty, markConflict, updatedAtBase])

  return {
    saveState,
    isDirty,
    error,
    updatedAtBase,
    etag,
    conflictInfo,
    scheduleSave,
    saveNow,
    retry,
    clearConflict,
    resetWithServerNote,
    resetWithServerVersion,
    saveMergedAfterConflict,
  }
}


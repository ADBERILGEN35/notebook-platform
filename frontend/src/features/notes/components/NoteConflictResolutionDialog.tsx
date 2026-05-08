import { Button } from '../../../shared/components/Button'
import { ResponsiveDrawer } from '../../../shared/components/ResponsiveDrawer'
import { useMediaQuery } from '../../../shared/hooks/useMediaQuery'
import type { NoteSaveSnapshot } from '../utils/note-save-snapshot'

type Props = {
  open: boolean
  onClose: () => void
  localSnapshot: NoteSaveSnapshot
  serverTitle: string
  serverPreview: string
  localPreview: string
  canSaveCopy: boolean
  onReloadLatest: () => void
  onSaveCopy: () => void
  onOverwrite: () => void
}

export function NoteConflictResolutionDialog(props: Props) {
  const isMobile = useMediaQuery('(max-width: 639px)')

  const content = (
    <div data-testid="conflict-dialog" className="space-y-3">
      <p className="text-sm text-slate-600">
        Your local changes were not saved because the note has a newer version on the server.
      </p>
      <section className="space-y-1 rounded border border-slate-200 p-2">
        <p className="text-xs font-semibold uppercase text-slate-500">Latest server version</p>
        <p className="text-sm font-medium text-slate-900">{props.serverTitle || '(untitled)'}</p>
        <p className="line-clamp-4 whitespace-pre-wrap break-words text-xs text-slate-600">{props.serverPreview || 'No text preview'}</p>
      </section>
      <section className="space-y-1 rounded border border-primary-200 bg-primary-50/30 p-2">
        <p className="text-xs font-semibold uppercase text-primary-700">Your unsaved changes</p>
        <p className="text-sm font-medium text-slate-900">{props.localSnapshot.title || '(untitled)'}</p>
        <p className="line-clamp-4 whitespace-pre-wrap break-words text-xs text-slate-700">{props.localPreview || 'No text preview'}</p>
      </section>
      <div className="flex flex-wrap gap-2">
        <Button data-testid="conflict-reload-latest" type="button" onClick={props.onReloadLatest}>
          Reload latest
        </Button>
        <Button
          data-testid="conflict-save-copy"
          type="button"
          onClick={props.onSaveCopy}
          disabled={!props.canSaveCopy}
          aria-label="Save local changes as a copy"
        >
          Save my changes as copy
        </Button>
        <Button
          data-testid="conflict-overwrite"
          type="button"
          className="bg-amber-600 text-white hover:bg-amber-700"
          onClick={props.onOverwrite}
        >
          Overwrite latest
        </Button>
        <Button data-testid="conflict-cancel" type="button" onClick={props.onClose}>
          Cancel
        </Button>
      </div>
      {!props.canSaveCopy ? <p className="text-xs text-slate-500">Copy action requires notebook context.</p> : null}
    </div>
  )

  if (!props.open) return null

  if (isMobile) {
    return (
      <ResponsiveDrawer open={props.open} onClose={props.onClose} title="This note changed elsewhere" testId="conflict-dialog">
        {content}
      </ResponsiveDrawer>
    )
  }

  return (
    <>
      <div className="fixed inset-0 z-40 bg-slate-900/40" onClick={props.onClose} aria-hidden />
      <div className="fixed inset-0 z-50 grid place-items-center p-4">
        <div className="w-full max-w-2xl rounded-lg border border-slate-200 bg-white p-4 shadow-xl">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-base font-semibold text-slate-900">This note changed elsewhere</h2>
            <button className="text-sm text-slate-600" onClick={props.onClose} aria-label="Close conflict dialog">
              Close
            </button>
          </div>
          {content}
        </div>
      </div>
    </>
  )
}

import { Button } from '../../../shared/components/Button'
import { ResponsiveDrawer } from '../../../shared/components/ResponsiveDrawer'
import { useMediaQuery } from '../../../shared/hooks/useMediaQuery'
import type { NoteSaveSnapshot } from '../utils/note-save-snapshot'
import { canAutoMerge, type MergeAnalysis } from '../utils/blocknote-merge'

type Props = {
  open: boolean
  onClose: () => void
  localSnapshot: NoteSaveSnapshot
  serverTitle: string
  serverPreview: string
  localPreview: string
  canSaveCopy: boolean
  mergeAnalysis: MergeAnalysis | null
  remoteLoading: boolean
  onReloadLatest: () => void
  onSaveCopy: () => void
  onOverwrite: () => void
  onApplySuggestedMerge?: () => void
}

function SummaryList({ lines }: { lines: string[] }) {
  if (!lines.length) return <p className="text-xs text-slate-500">No changes detected.</p>
  return (
    <ul className="list-inside list-disc space-y-0.5 text-xs text-slate-700">
      {lines.slice(0, 24).map((line, i) => (
        <li key={i} className="break-words">
          {line.length > 200 ? `${line.slice(0, 200)}…` : line}
        </li>
      ))}
    </ul>
  )
}

export function NoteConflictResolutionDialog(props: Props) {
  const isMobile = useMediaQuery('(max-width: 639px)')
  const showMerge = props.mergeAnalysis && canAutoMerge(props.mergeAnalysis)
  const overlapConflict = Boolean(
    props.mergeAnalysis?.conflicts.some((c) =>
      ['same_block_divergent', 'delete_vs_edit', 'title_divergent', 'duplicate_block_id', 'missing_block_id', 'unknown_block_type'].includes(
        c.reason,
      ),
    ),
  )
  const hasMoveConflict = Boolean(
    props.mergeAnalysis?.conflicts.some((c) =>
      ['block_move_conflict', 'block_moved_and_edited', 'block_deleted_after_move', 'block_cross_parent_unsupported', 'move_or_structure'].includes(
        c.reason,
      ),
    ),
  )
  const movedBlocks = props.mergeAnalysis?.movedBlocks ?? []
  const reorderedBlocks = props.mergeAnalysis?.reorderedBlocks ?? []
  const moveConflictRows = props.mergeAnalysis?.moveConflicts ?? []

  const guidance = (() => {
    if (props.remoteLoading || !props.mergeAnalysis) {
      return 'Loading the latest server version to compare changes…'
    }
    if (showMerge && (movedBlocks.length || reorderedBlocks.length)) {
      return 'We can safely combine your block order changes with server content changes. Review the suggested merge before applying.'
    }
    if (showMerge) {
      return 'We can safely combine these changes. Suggested merge keeps non-overlapping changes from both versions.'
    }
    if (hasMoveConflict) {
      return 'Block reordering or moves overlap with other changes. Review manually or save your copy.'
    }
    if (overlapConflict) {
      return 'Some changes touch the same block and need your decision. Review manually or save your copy.'
    }
    if (!props.mergeAnalysis.suggestion && !props.mergeAnalysis.conflicts.length) {
      return 'No automatic merge was available for this combination.'
    }
    return 'No automatic merge was applied.'
  })()

  const content = (
    <div data-testid="conflict-dialog" className="space-y-3">
      <p className="text-sm text-slate-600">
        Your local changes were not saved because the note has a newer version on the server.
      </p>
      <p className="text-sm font-medium text-slate-800">{guidance}</p>
      <section className="space-y-1 rounded border border-slate-200 p-2">
        <p className="text-xs font-semibold uppercase text-slate-500">Latest server version</p>
        <p className="text-sm font-medium text-slate-900">{props.serverTitle || '(untitled)'}</p>
        <p className="line-clamp-4 whitespace-pre-wrap break-words text-xs text-slate-600">
          {props.serverPreview || 'No text preview'}
        </p>
      </section>
      <section className="space-y-1 rounded border border-primary-200 bg-primary-50/30 p-2">
        <p className="text-xs font-semibold uppercase text-primary-700">Your unsaved changes</p>
        <p className="text-sm font-medium text-slate-900">{props.localSnapshot.title || '(untitled)'}</p>
        <p className="line-clamp-4 whitespace-pre-wrap break-words text-xs text-slate-700">
          {props.localPreview || 'No text preview'}
        </p>
      </section>
      {props.mergeAnalysis ? (
        <div className="space-y-2 rounded border border-slate-100 bg-slate-50/80 p-2">
          <p className="text-xs font-semibold text-slate-700">What changed?</p>
          <details className="rounded border border-slate-200 bg-white p-2" open={!isMobile}>
            <summary className="cursor-pointer text-xs font-medium text-slate-800">Local changes</summary>
            <div className="mt-2" data-testid="conflict-local-diff-summary">
              <SummaryList lines={props.mergeAnalysis.localChangeSummary} />
            </div>
          </details>
          <details className="rounded border border-slate-200 bg-white p-2" open={!isMobile}>
            <summary className="cursor-pointer text-xs font-medium text-slate-800">Server changes</summary>
            <div className="mt-2" data-testid="conflict-remote-diff-summary">
              <SummaryList lines={props.mergeAnalysis.remoteChangeSummary} />
            </div>
          </details>
          {movedBlocks.length ? (
            <details className="rounded border border-sky-200 bg-sky-50/40 p-2" open={!isMobile}>
              <summary className="cursor-pointer text-xs font-medium text-sky-900">Moved blocks</summary>
              <div className="mt-2" data-testid="conflict-moved-blocks-summary">
                <SummaryList
                  lines={movedBlocks.map((m) =>
                    m.parentChanged
                      ? `Moved ${m.blockType} block ${m.blockId} to a different parent`
                      : `Moved ${m.blockType} block ${m.blockId}`,
                  )}
                />
              </div>
            </details>
          ) : null}
          {reorderedBlocks.length ? (
            <details className="rounded border border-sky-200 bg-sky-50/40 p-2" open={!isMobile}>
              <summary className="cursor-pointer text-xs font-medium text-sky-900">Reordered blocks</summary>
              <div className="mt-2" data-testid="conflict-reorder-summary">
                <SummaryList
                  lines={reorderedBlocks.map(
                    (m) => `Reordered ${m.blockType} block ${m.blockId} from position ${m.fromIndex ?? '?'} to ${m.toIndex ?? '?'}`,
                  )}
                />
              </div>
            </details>
          ) : null}
          {moveConflictRows.length ? (
            <details className="rounded border border-amber-200 bg-amber-50/50 p-2" open>
              <summary className="cursor-pointer text-xs font-medium text-amber-900">Move conflicts</summary>
              <div className="mt-2" data-testid="conflict-move-conflicts-summary">
                <SummaryList
                  lines={moveConflictRows.map(
                    (m) => `Block ${m.blockId} was moved differently in both versions.`,
                  )}
                />
              </div>
            </details>
          ) : null}
          {props.mergeAnalysis.conflictSummaries.length ? (
            <details className="rounded border border-amber-200 bg-amber-50/50 p-2" open>
              <summary className="cursor-pointer text-xs font-medium text-amber-900">Conflicts</summary>
              <div className="mt-2" data-testid="conflict-conflicts-summary">
                <SummaryList lines={props.mergeAnalysis.conflictSummaries} />
              </div>
            </details>
          ) : null}
        </div>
      ) : null}
      <div
        className={`flex flex-wrap gap-2${isMobile ? ' sticky bottom-0 border-t border-slate-200 bg-white pt-3' : ''}`}
        role="group"
        aria-label="Conflict resolution actions"
      >
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
        {showMerge && props.onApplySuggestedMerge ? (
          <Button
            data-testid="conflict-apply-merge"
            type="button"
            className="bg-emerald-600 text-white hover:bg-emerald-700"
            onClick={props.onApplySuggestedMerge}
            aria-label="Apply suggested merge"
          >
            Apply suggested merge
          </Button>
        ) : null}
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
      <ResponsiveDrawer
        open={props.open}
        onClose={props.onClose}
        title="This note changed elsewhere"
        testId="conflict-dialog"
      >
        {content}
      </ResponsiveDrawer>
    )
  }

  return (
    <>
      <div className="fixed inset-0 z-40 bg-slate-900/40" onClick={props.onClose} aria-hidden />
      <div className="fixed inset-0 z-50 grid place-items-center p-4">
        <div
          className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-lg border border-slate-200 bg-white p-4 shadow-xl"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="conflict-dialog-title"
        >
          <div className="mb-3 flex items-center justify-between">
            <h2 id="conflict-dialog-title" className="text-base font-semibold text-slate-900">
              This note changed elsewhere
            </h2>
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

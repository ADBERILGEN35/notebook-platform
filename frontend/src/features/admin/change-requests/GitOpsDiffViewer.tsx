import { useMemo } from 'react'
import { parseUnifiedDiff } from './parse-unified-diff'
import { DiffLine } from './DiffLine'
import { DiffFileHeader } from './DiffFileHeader'

type GitOpsDiffViewerProps = {
  diffPreview: string
  environment?: string
  operationType?: string
  filePath?: string
  mode?: 'unified' | 'side-by-side'
  compact?: boolean
}

export function GitOpsDiffViewer({
  diffPreview,
  environment,
  operationType,
  filePath,
  mode = 'unified',
  compact = false,
}: GitOpsDiffViewerProps) {
  const hunks = useMemo(() => parseUnifiedDiff(diffPreview), [diffPreview])
  const sideBySide = mode === 'side-by-side'

  if (!diffPreview.trim()) {
    return (
      <p className="rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-950" role="alert">
        No diff lines to display. Run dry-run again or check the change request payload.
      </p>
    )
  }

  const scrollClass = compact ? 'max-h-[min(60vh,28rem)]' : 'max-h-96'

  return (
    <div className="space-y-3" data-testid="gitops-diff-viewer">
      <DiffFileHeader path={filePath} environment={environment} operationType={operationType} />
      <p className="text-xs text-slate-500">Preview only — no runtime mutation. Secret-like values are masked.</p>

      {sideBySide ? (
        <div className="grid overflow-hidden rounded border border-slate-200 md:grid-cols-2" data-testid="gitops-diff-side-by-side">
          <div className="border-r border-slate-200 bg-slate-50/50">
            <p className="border-b border-slate-200 px-2 py-1 text-[10px] font-semibold uppercase text-slate-500">Before</p>
            <div className={`${scrollClass} overflow-auto`} tabIndex={0} aria-label="Diff before column">
              {hunks.flatMap((h) =>
                h.lines
                  .filter((l) => l.kind !== 'add')
                  .map((l, i) => <DiffLine key={`old-${h.header}-${i}`} line={l} />),
              )}
            </div>
          </div>
          <div>
            <p className="border-b border-slate-200 px-2 py-1 text-[10px] font-semibold uppercase text-slate-500">After</p>
            <div className={`${scrollClass} overflow-auto`} tabIndex={0} aria-label="Diff after column">
              {hunks.flatMap((h) =>
                h.lines
                  .filter((l) => l.kind !== 'remove')
                  .map((l, i) => <DiffLine key={`new-${h.header}-${i}`} line={l} />),
              )}
            </div>
          </div>
        </div>
      ) : null}

      <div
        className={`overflow-hidden rounded border border-slate-200 ${sideBySide ? 'md:hidden' : ''}`}
        data-testid="gitops-diff-unified"
        role="region"
        aria-label="Unified YAML diff preview"
      >
        <p className="border-b border-slate-200 bg-slate-50 px-2 py-1 text-[10px] font-semibold uppercase text-slate-500">
          Unified diff
        </p>
        <div className={`${scrollClass} overflow-auto`} tabIndex={0}>
          {hunks.map((h) => (
            <div key={h.header}>
              <p className="bg-slate-100 px-2 py-1 font-mono text-[10px] text-slate-600">{h.header}</p>
              {h.lines.map((l, i) => (
                <DiffLine key={`${h.header}-${i}`} line={l} />
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

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
}

export function GitOpsDiffViewer({
  diffPreview,
  environment,
  operationType,
  filePath,
  mode = 'unified',
}: GitOpsDiffViewerProps) {
  const hunks = useMemo(() => parseUnifiedDiff(diffPreview), [diffPreview])

  const sideBySide = mode === 'side-by-side'

  return (
    <div className="space-y-3" data-testid="gitops-diff-viewer">
      <DiffFileHeader path={filePath} environment={environment} operationType={operationType} />
      <p className="text-xs text-slate-500">Preview only — no runtime mutation. Secret-like values are masked.</p>

      {sideBySide ? (
        <div className="grid overflow-hidden rounded border border-slate-200 md:grid-cols-2">
          <div className="border-r border-slate-200 bg-slate-50/50">
            <p className="border-b border-slate-200 px-2 py-1 text-[10px] font-semibold uppercase text-slate-500">Before</p>
            <div className="max-h-96 overflow-auto">
              {hunks.flatMap((h) =>
                h.lines
                  .filter((l) => l.kind !== 'add')
                  .map((l, i) => <DiffLine key={`old-${h.header}-${i}`} line={l} />),
              )}
            </div>
          </div>
          <div>
            <p className="border-b border-slate-200 px-2 py-1 text-[10px] font-semibold uppercase text-slate-500">After</p>
            <div className="max-h-96 overflow-auto">
              {hunks.flatMap((h) =>
                h.lines
                  .filter((l) => l.kind !== 'remove')
                  .map((l, i) => <DiffLine key={`new-${h.header}-${i}`} line={l} />),
              )}
            </div>
          </div>
        </div>
      ) : (
        <div className="overflow-hidden rounded border border-slate-200">
          <p className="border-b border-slate-200 bg-slate-50 px-2 py-1 text-[10px] font-semibold uppercase text-slate-500">
            Unified diff
          </p>
          <div className="max-h-96 overflow-auto">
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
      )}
    </div>
  )
}

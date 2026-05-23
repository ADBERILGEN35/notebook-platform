import type { ParsedDiffLine } from './parse-unified-diff'

const kindClass: Record<ParsedDiffLine['kind'], string> = {
  add: 'bg-emerald-50 text-emerald-950',
  remove: 'bg-red-50 text-red-950',
  context: 'bg-white text-slate-800',
  header: 'bg-slate-100 text-slate-600 font-semibold',
}

const kindLabel: Record<ParsedDiffLine['kind'], string> = {
  add: 'Added line',
  remove: 'Removed line',
  context: 'Context line',
  header: 'Diff header',
}

export function DiffLine({ line }: { line: ParsedDiffLine }) {
  const symbol = line.kind === 'add' ? '+' : line.kind === 'remove' ? '−' : line.kind === 'header' ? '@' : ' '
  return (
    <div className={`grid grid-cols-[3rem_3rem_1.5rem_1fr] gap-1 font-mono text-[11px] leading-5 ${kindClass[line.kind]}`}>
      <span className="select-none text-right text-slate-400">{line.oldLineNo ?? ''}</span>
      <span className="select-none text-right text-slate-400">{line.newLineNo ?? ''}</span>
      <span className="select-none text-center text-slate-500" aria-hidden="true">
        {symbol}
      </span>
      <span className="sr-only">{kindLabel[line.kind]}</span>
      <span className="whitespace-pre-wrap break-all">{line.content}</span>
    </div>
  )
}

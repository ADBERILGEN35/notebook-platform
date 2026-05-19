import { maskDiffLineContent } from './diff-mask'

export type ParsedDiffLine = {
  kind: 'context' | 'add' | 'remove' | 'header'
  oldLineNo: number | null
  newLineNo: number | null
  content: string
}

export type ParsedDiffHunk = {
  header: string
  lines: ParsedDiffLine[]
}

export function parseUnifiedDiff(diffText: string): ParsedDiffHunk[] {
  const hunks: ParsedDiffHunk[] = []
  let current: ParsedDiffHunk | null = null
  let oldNo = 0
  let newNo = 0

  for (const raw of diffText.split('\n')) {
    const line = raw.replace(/\r$/, '')
    if (line.startsWith('@@')) {
      current = { header: line, lines: [] }
      hunks.push(current)
      const m = /@@ -(\d+)(?:,\d+)? \+(\d+)/.exec(line)
      oldNo = m ? Number(m[1]) : 0
      newNo = m ? Number(m[2]) : 0
      continue
    }
    if (!current) {
      if (line.startsWith('---') || line.startsWith('+++')) {
        hunks.push({ header: line, lines: [] })
        current = hunks[hunks.length - 1]
      }
      continue
    }

    const prefix = line[0]
    const content = maskDiffLineContent(line.slice(1) || line)
    if (prefix === '+') {
      current.lines.push({ kind: 'add', oldLineNo: null, newLineNo: newNo++, content })
    } else if (prefix === '-') {
      current.lines.push({ kind: 'remove', oldLineNo: oldNo++, newLineNo: null, content })
    } else if (prefix === ' ') {
      current.lines.push({ kind: 'context', oldLineNo: oldNo++, newLineNo: newNo++, content })
    } else {
      current.lines.push({ kind: 'header', oldLineNo: null, newLineNo: null, content: maskDiffLineContent(line) })
    }
  }

  return hunks
}

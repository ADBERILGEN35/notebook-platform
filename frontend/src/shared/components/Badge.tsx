import type { PropsWithChildren } from 'react'

export function Badge({ children }: PropsWithChildren) {
  return <span className="rounded border border-slate-200 bg-slate-50 px-2 py-1 text-xs">{children}</span>
}


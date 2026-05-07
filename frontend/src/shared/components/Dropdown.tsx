import type { PropsWithChildren } from 'react'

export function Dropdown({ children }: PropsWithChildren) {
  return <div className="rounded-md border border-slate-200 bg-white p-2 shadow-card">{children}</div>
}


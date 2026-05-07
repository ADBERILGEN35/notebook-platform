import type { PropsWithChildren } from 'react'

export function Card({ children }: PropsWithChildren) {
  return <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-card">{children}</div>
}


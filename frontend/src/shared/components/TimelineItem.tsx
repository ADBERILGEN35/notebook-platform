import type { ReactNode } from 'react'

type TimelineItemProps = {
  title: string
  timestamp: string
  description?: string
  icon?: ReactNode
}

export function TimelineItem({ title, timestamp, description, icon }: TimelineItemProps) {
  return (
    <li className="relative flex gap-3 pb-6 last:pb-0">
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-fixed text-primary" aria-hidden>
        {icon ?? <span className="text-label-md font-semibold">•</span>}
      </div>
      <div className="min-w-0 flex-1">
        <p className="font-medium text-body-md text-on-surface">{title}</p>
        <time className="text-label-md text-on-surface-variant" dateTime={timestamp}>
          {new Date(timestamp).toLocaleString()}
        </time>
        {description ? <p className="mt-1 text-body-md text-on-surface-variant">{description}</p> : null}
      </div>
    </li>
  )
}

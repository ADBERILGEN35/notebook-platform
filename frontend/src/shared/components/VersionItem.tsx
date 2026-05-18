import { Button } from './Button'

type VersionItemProps = {
  versionNumber: number
  title: string
  createdAt: string
  onPreview?: () => void
  onRestore?: () => void
  restoreDisabled?: boolean
}

export function VersionItem({ versionNumber, title, createdAt, onPreview, onRestore, restoreDisabled }: VersionItemProps) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2">
      <div className="min-w-0">
        <p className="font-medium text-body-md text-on-surface">
          v{versionNumber} — {title}
        </p>
        <time className="text-label-md text-on-surface-variant" dateTime={createdAt}>
          {new Date(createdAt).toLocaleString()}
        </time>
      </div>
      <div className="flex shrink-0 gap-2">
        {onPreview ? (
          <Button type="button" className="text-label-md" onClick={onPreview}>
            Preview
          </Button>
        ) : null}
        {onRestore ? (
          <Button type="button" className="text-label-md" onClick={onRestore} disabled={restoreDisabled}>
            Restore
          </Button>
        ) : null}
      </div>
    </li>
  )
}

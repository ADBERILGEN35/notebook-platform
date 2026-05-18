import { Button } from '../../shared/components/Button'
import { InlineStatus } from '../../shared/components/InlineStatus'

type SessionRowProps = {
  deviceLabel: string
  lastActive: string
  current?: boolean
  onRevoke?: () => void
  revokeDisabled?: boolean
}

export function SessionRow({ deviceLabel, lastActive, current, onRevoke, revokeDisabled }: SessionRowProps) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-outline-variant px-4 py-3">
      <div>
        <p className="font-medium text-body-md text-on-surface">{deviceLabel}</p>
        <p className="text-label-md text-on-surface-variant">Last active {lastActive}</p>
      </div>
      <div className="flex items-center gap-2">
        {current ? <InlineStatus label="This device" tone="success" /> : null}
        {onRevoke ? (
          <Button type="button" className="text-label-md" onClick={onRevoke} disabled={revokeDisabled || current}>
            Revoke
          </Button>
        ) : null}
      </div>
    </li>
  )
}

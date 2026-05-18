import { InlineStatus } from '../../../shared/components/InlineStatus'
import { PanelCard } from '../../../shared/components/PanelCard'

type AccessRequestPanelProps = {
  status: 'idle' | 'pending' | 'sent'
  resourceLabel: string
}

export function AccessRequestPanel({ status, resourceLabel }: AccessRequestPanelProps) {
  return (
    <PanelCard title="Access request" subtitle={resourceLabel}>
      {status === 'idle' ? (
        <p className="text-body-md text-on-surface-variant">
          Submit a request and workspace admins will be notified. You will see updates in notifications when a decision is
          made.
        </p>
      ) : null}
      {status === 'pending' || status === 'sent' ? (
        <div className="space-y-2">
          <InlineStatus label={status === 'sent' ? 'Request sent' : 'Pending review'} tone="warning" />
          <p className="text-body-md text-on-surface-variant">
            Your request is being reviewed. You can continue using areas of the workspace you already have access to.
          </p>
        </div>
      ) : null}
    </PanelCard>
  )
}

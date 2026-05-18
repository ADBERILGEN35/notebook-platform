import type { ReactNode } from 'react'
import { Button } from '../../../shared/components/Button'
import { PanelCard } from '../../../shared/components/PanelCard'
import { sanitizeErrorMessage } from '../../../shared/lib/sanitize-error-message'

type AccessDeniedStateProps = {
  title?: string
  message?: string
  onRequestAccess?: () => void
  requestPending?: boolean
  extra?: ReactNode
}

export function AccessDeniedState({
  title = 'Access denied',
  message = 'You do not have permission to view this resource. Ask a workspace admin for access or request access below.',
  onRequestAccess,
  requestPending,
  extra,
}: AccessDeniedStateProps) {
  return (
    <PanelCard title={title} subtitle="Permission required">
      <p className="text-body-md text-on-surface-variant">{sanitizeErrorMessage(message, message)}</p>
      {extra}
      {onRequestAccess ? (
        <div className="mt-4">
          <Button
            type="button"
            className="bg-primary text-white hover:bg-primary-container"
            onClick={onRequestAccess}
            disabled={requestPending}
          >
            {requestPending ? 'Request pending' : 'Request access'}
          </Button>
        </div>
      ) : null}
    </PanelCard>
  )
}

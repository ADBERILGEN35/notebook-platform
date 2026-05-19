import { Link } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_NOTIFICATIONS_LEGAL_HOLD_READ,
  PERM_NOTIFICATIONS_RETENTION_READ,
  PERM_RETENTION_READ,
  PERM_RETENTION_LEGAL_HOLD_WRITE,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import {
  isNotificationLegalHoldUiEnabled,
  isNotificationRetentionUiEnabled,
  isPlatformRetentionGovernanceUiEnabled,
} from '../../shared/config/admin-feature-flags'

export function AdminRetentionHubPage() {
  const user = useAuthStore((s) => s.user)
  const showNotificationRetention =
    isNotificationRetentionUiEnabled() && hasPlatformPermission(user, PERM_NOTIFICATIONS_RETENTION_READ)
  const showPlatform =
    isPlatformRetentionGovernanceUiEnabled() && hasPlatformPermission(user, PERM_RETENTION_READ)
  const showLegalHolds =
    (isNotificationLegalHoldUiEnabled() && hasPlatformPermission(user, PERM_NOTIFICATIONS_LEGAL_HOLD_READ)) ||
  (isPlatformRetentionGovernanceUiEnabled() &&
      (hasPlatformPermission(user, PERM_RETENTION_READ) ||
        hasPlatformPermission(user, PERM_RETENTION_LEGAL_HOLD_WRITE)))

  return (
    <div className="space-y-4" data-testid="admin-retention-hub">
      <PageHeader
        title="Retention governance"
        subtitle="Aggregate operational retention — no row-level sensitive content."
      />
      <div className="grid gap-3 md:grid-cols-2">
        {showNotificationRetention ? (
          <Card className="p-4">
            <p className="font-semibold text-slate-900">Notification retention</p>
            <p className="mt-1 text-sm text-slate-600">Targets, dry-run plans, legal-hold blocks (notification-service).</p>
            <Link className="mt-2 inline-block text-sm text-primary-700 underline" to="/app/admin/notifications/retention">
              Open notification retention
            </Link>
          </Card>
        ) : null}
        {showPlatform ? (
          <Card className="p-4">
            <p className="font-semibold text-slate-900">Platform retention</p>
            <p className="mt-1 text-sm text-slate-600">Cross-service summaries (content, search, workspace, notifications).</p>
            <Link className="mt-2 inline-block text-sm text-primary-700 underline" to="/app/admin/retention/platform">
              Open platform governance
            </Link>
          </Card>
        ) : null}
        {showLegalHolds ? (
          <Card className="p-4">
            <p className="font-semibold text-slate-900">Legal holds</p>
            <p className="mt-1 text-sm text-slate-600">Hold scope and blocked targets — no sensitive case documents.</p>
            <Link className="mt-2 inline-block text-sm text-primary-700 underline" to="/app/admin/retention/legal-holds">
              Manage legal holds
            </Link>
          </Card>
        ) : null}
        <Card className="p-4">
          <p className="font-semibold text-slate-900">Purge results</p>
          <p className="mt-1 text-sm text-slate-600">Audit-ready summary after a recorded purge (session-scoped).</p>
          <Link className="mt-2 inline-block text-sm text-primary-700 underline" to="/app/admin/retention/purge-result">
            View purge result
          </Link>
        </Card>
      </div>
    </div>
  )
}

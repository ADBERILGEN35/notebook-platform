import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { useAuthStore } from '../../features/auth/auth-store'
import { PERM_NOTIFICATIONS_RETENTION_READ, hasPlatformPermission } from '../../features/admin/access/admin-permissions'
import { isNotificationRetentionUiEnabled } from '../../shared/config/admin-feature-flags'
import { PurgeResultSummary } from '../../features/admin/retention/PurgeResultSummary'
import { loadPurgeResult } from '../../features/admin/retention/purge-result-storage'

export function AdminPurgeResultPage() {
  const user = useAuthStore((s) => s.user)
  const canRead = hasPlatformPermission(user, PERM_NOTIFICATIONS_RETENTION_READ)
  const payload = useMemo(() => loadPurgeResult(), [])

  if (!isNotificationRetentionUiEnabled()) {
    return (
      <div className="space-y-3">
        <PageHeader title="Purge result" subtitle="Audit-ready summary" />
        <Card className="p-4 text-sm text-slate-600">Retention UI is disabled.</Card>
      </div>
    )
  }

  if (!canRead) {
    return (
      <div className="space-y-3">
        <PageHeader title="Purge result" subtitle="Audit-ready summary" />
        <Card className="p-4 text-sm text-slate-600">You do not have permission to view retention results.</Card>
      </div>
    )
  }

  return (
    <div className="space-y-4" data-testid="purge-result-page">
      <PageHeader title="Purge result" subtitle="Aggregate counts only — no row-level content." />
      <p className="text-sm">
        <Link className="text-primary-700 underline" to="/app/admin/retention">
          Retention hub
        </Link>
        {' · '}
        <Link className="text-primary-700 underline" to="/app/admin/notifications/retention">
          Notification retention
        </Link>
      </p>
      {payload ? (
        <PurgeResultSummary payload={payload} />
      ) : (
        <Card className="p-4 text-sm text-slate-600">
          No purge result in this browser session. Complete a purge from notification retention (when enabled) or record a
          dry-run for planning only.
        </Card>
      )}
    </div>
  )
}

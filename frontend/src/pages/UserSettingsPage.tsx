import { Link } from 'react-router-dom'
import { useAuthStore } from '../features/auth/auth-store'
import { PageHeader } from '../shared/components/PageHeader'
import { SectionCard } from '../shared/components/SectionCard'
import { InfoRow } from '../shared/components/InfoRow'
import { Input } from '../shared/components/Input'
import { Button } from '../shared/components/Button'
import { InlineStatus } from '../shared/components/InlineStatus'

export function UserSettingsPage() {
  const user = useAuthStore((s) => s.user)

  return (
    <div className="space-y-6">
      <PageHeader title="Profile" subtitle="Your account overview and preferences entry points." />
      <SectionCard title="Account" description="Profile fields are read-only until profile API ships.">
        <InfoRow label="Name" value={user?.name || '—'} />
        <InfoRow label="Email" value={user?.email || '—'} />
        <InfoRow
          label="User ID"
          value={user?.id ? `${user.id.slice(0, 8)}…` : '—'}
          hint="Internal identifier only — not a secret."
        />
        <label className="mt-4 block text-label-md text-on-surface-variant">
          Display name (preview)
          <Input className="mt-1" defaultValue={user?.name || ''} disabled aria-label="Display name" />
        </label>
        <Button type="button" className="mt-3" disabled title="Profile update API not connected">
          Save profile
        </Button>
        <p className="mt-2">
          <InlineStatus label="Read-only shell" tone="neutral" />
        </p>
      </SectionCard>
      <SectionCard title="Quick links">
        <ul className="space-y-2 text-body-md">
          <li>
            <Link to="/app/settings/security" className="text-primary hover:underline">
              Security & sessions
            </Link>
          </li>
          <li>
            <Link to="/app/settings/notifications" className="text-primary hover:underline">
              Notification preferences
            </Link>
          </li>
          <li>
            <Link to="/app/settings/sync" className="text-primary hover:underline">
              Offline & sync diagnostics
            </Link>
          </li>
        </ul>
      </SectionCard>
    </div>
  )
}

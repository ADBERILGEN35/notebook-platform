import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { LoadingState } from '../../shared/components/LoadingState'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_RETENTION_LEGAL_HOLD_WRITE,
  PERM_RETENTION_READ,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import { isPlatformRetentionGovernanceUiEnabled } from '../../shared/config/admin-feature-flags'
import {
  createPlatformLegalHold,
  fetchPlatformLegalHolds,
  releasePlatformLegalHold,
  type PlatformLegalHoldList,
} from '../../features/admin/platform-retention-api'
import { LegalHoldCard } from '../../features/admin/retention/LegalHoldCard'
import { maskActorId } from '../../features/admin/notifications/notification-ops-utils'

const scopeOptions = ['ALL_PLATFORM', 'CONTENT', 'IDENTITY', 'AUDIT', 'NOTIFICATION', 'WORKSPACE', 'USER', 'NOTE']

export function AdminPlatformLegalHoldsPage() {
  const user = useAuthStore((s) => s.user)
  const canRead = hasPlatformPermission(user, PERM_RETENTION_READ)
  const canWrite = hasPlatformPermission(user, PERM_RETENTION_LEGAL_HOLD_WRITE)
  const uiEnabled = isPlatformRetentionGovernanceUiEnabled()
  const [holds, setHolds] = useState<PlatformLegalHoldList | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [holdKey, setHoldKey] = useState('')
  const [scope, setScope] = useState('ALL_PLATFORM')
  const [scopeRefId, setScopeRefId] = useState('')
  const [reason, setReason] = useState('')
  const [releaseId, setReleaseId] = useState('')
  const [releaseReason, setReleaseReason] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setHolds(await fetchPlatformLegalHolds())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load legal holds')
      setHolds(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!uiEnabled || !canRead) return
    void load()
  }, [uiEnabled, canRead, load])

  const onCreate = async () => {
    if (!canWrite) return
    if (reason.trim().length < 10) {
      setError('Reason must be at least 10 characters.')
      return
    }
    if (!holdKey.trim()) {
      setError('Hold key is required.')
      return
    }
    try {
      await createPlatformLegalHold({
        holdKey: holdKey.trim(),
        scope,
        scopeRefId: scopeRefId.trim() || undefined,
        reason: reason.trim(),
      })
      setHoldKey('')
      setScope('ALL_PLATFORM')
      setScopeRefId('')
      setReason('')
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Create hold failed')
    }
  }

  const onRelease = async () => {
    if (!canWrite || !releaseId) return
    if (releaseReason.trim().length < 10) {
      setError('Release reason must be at least 10 characters.')
      return
    }
    try {
      await releasePlatformLegalHold(releaseId, releaseReason.trim())
      setReleaseId('')
      setReleaseReason('')
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Release hold failed')
    }
  }

  if (!uiEnabled) {
    return (
      <div className="space-y-3">
        <PageHeader title="Legal holds" subtitle="Platform scope" />
        <Card className="p-4 text-sm text-slate-600">
          Platform retention governance UI is disabled. Legal hold management requires the platform retention feature
          flag.
        </Card>
      </div>
    )
  }

  if (!canRead) {
    return (
      <div className="space-y-3">
        <PageHeader title="Legal holds" subtitle="Platform scope" />
        <Card className="p-4 text-sm text-slate-600">You do not have permission to view platform legal holds.</Card>
      </div>
    )
  }

  const items = holds?.items ?? []

  return (
    <div className="space-y-4" data-testid="legal-holds-management">
      <PageHeader
        title="Legal holds"
        subtitle="Hold scope and blocked targets — sensitive legal text and case documents are not shown."
      />
      <p className="text-sm">
        <Link className="text-primary-700 underline" to="/app/admin/retention">
          Retention hub
        </Link>
        {' · '}
        <Link className="text-primary-700 underline" to="/app/admin/notifications/legal-holds">
          Notification-scoped holds
        </Link>
      </p>
      {error ? <ErrorAlert message={error} /> : null}
      {loading ? <LoadingState label="Loading legal holds" /> : null}
      {!loading && items.length === 0 ? (
        <Card className="p-4 text-sm text-slate-600">No legal holds returned.</Card>
      ) : null}
      <div className="grid gap-3 lg:grid-cols-2">
        {items.map((h) => (
          <LegalHoldCard
            key={h.id}
            hold={{
              id: h.id,
              holdKey: h.holdKey,
              scope: h.scope,
              status: h.status,
              createdAt: h.createdAt,
              expiresAt: h.expiresAt,
              ownerLabel: maskActorId(h.createdByUserId),
            }}
          />
        ))}
      </div>
      {canWrite ? (
        <div className="grid gap-3 lg:grid-cols-2">
          <Card className="space-y-2 p-4">
            <p className="text-sm font-semibold">Create hold</p>
            <input className="w-full rounded border p-2 text-sm" placeholder="holdKey" value={holdKey} onChange={(e) => setHoldKey(e.target.value)} />
            <select className="w-full rounded border p-2 text-sm" value={scope} onChange={(e) => setScope(e.target.value)}>
              {scopeOptions.map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
            <input className="w-full rounded border p-2 text-sm" placeholder="scopeRefId (optional UUID)" value={scopeRefId} onChange={(e) => setScopeRefId(e.target.value)} />
            <textarea className="w-full rounded border p-2 text-sm" rows={3} placeholder="Reason (min 10 chars)" value={reason} onChange={(e) => setReason(e.target.value)} />
            <button type="button" className="rounded bg-slate-800 px-3 py-1.5 text-sm text-white" onClick={() => void onCreate()}>
              Create hold
            </button>
          </Card>
          <Card className="space-y-2 p-4">
            <p className="text-sm font-semibold">Release hold</p>
            <select className="w-full rounded border p-2 text-sm" value={releaseId} onChange={(e) => setReleaseId(e.target.value)}>
              <option value="">Select active hold</option>
              {items.filter((h) => h.status === 'ACTIVE').map((h) => (
                <option key={h.id} value={h.id}>
                  {h.holdKey}
                </option>
              ))}
            </select>
            <textarea className="w-full rounded border p-2 text-sm" rows={3} placeholder="Release reason" value={releaseReason} onChange={(e) => setReleaseReason(e.target.value)} />
            <button type="button" className="rounded border border-slate-300 px-3 py-1.5 text-sm" onClick={() => void onRelease()}>
              Release hold
            </button>
          </Card>
        </div>
      ) : (
        <Card className="p-4 text-sm text-slate-600">
          Legal hold writes require retention legal-hold permission and MFA when enforced.
        </Card>
      )}
    </div>
  )
}

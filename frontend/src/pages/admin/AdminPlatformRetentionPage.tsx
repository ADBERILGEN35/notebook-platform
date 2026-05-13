import { useCallback, useEffect, useMemo, useState } from 'react'
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
  fetchPlatformRetentionPlan,
  fetchPlatformRetentionTargets,
  releasePlatformLegalHold,
  type PlatformLegalHoldList,
  type RetentionPlanResponse,
  type RetentionTargetsResponse,
} from '../../features/admin/platform-retention-api'

const scopeOptions = ['ALL_PLATFORM', 'CONTENT', 'IDENTITY', 'AUDIT', 'NOTIFICATION', 'WORKSPACE', 'USER', 'NOTE']

export function AdminPlatformRetentionPage() {
  const user = useAuthStore((s) => s.user)
  const canRead = hasPlatformPermission(user, PERM_RETENTION_READ)
  const canWrite = hasPlatformPermission(user, PERM_RETENTION_LEGAL_HOLD_WRITE)
  const [targets, setTargets] = useState<RetentionTargetsResponse | null>(null)
  const [plan, setPlan] = useState<RetentionPlanResponse | null>(null)
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
      const [targetResponse, planResponse, holdResponse] = await Promise.all([
        fetchPlatformRetentionTargets(),
        fetchPlatformRetentionPlan(),
        fetchPlatformLegalHolds(),
      ])
      setTargets(targetResponse)
      setPlan(planResponse)
      setHolds(holdResponse)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to load platform retention governance')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!isPlatformRetentionGovernanceUiEnabled() || !canRead) return
    void load()
  }, [canRead, load])

  const overview = useMemo(() => {
    const rows = targets?.targets ?? []
    const holdsRows = holds?.items ?? []
    return {
      total: rows.length,
      dryRunReady: rows.filter((t) => t.status === 'DRY_RUN_READY').length,
      inventoryOnly: rows.filter((t) => t.status === 'INVENTORY_ONLY').length,
      legalHoldSupported: rows.filter((t) => t.legalHoldSupported).length,
      activeHolds: holdsRows.filter((h) => h.status === 'ACTIVE').length,
      highRisk: rows.filter((t) => t.riskLevel === 'HIGH' || t.riskLevel === 'CRITICAL').length,
    }
  }, [targets, holds])

  const onCreate = async () => {
    if (reason.trim().length < 10) {
      setError('Reason must be at least 10 characters.')
      return
    }
    if (!holdKey.trim()) {
      setError('Hold key is required.')
      return
    }
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
  }

  const onRelease = async () => {
    if (!releaseId) {
      setError('Select an active hold to release.')
      return
    }
    if (releaseReason.trim().length < 10) {
      setError('Release reason must be at least 10 characters.')
      return
    }
    await releasePlatformLegalHold(releaseId, releaseReason.trim())
    setReleaseId('')
    setReleaseReason('')
    await load()
  }

  if (!isPlatformRetentionGovernanceUiEnabled()) {
    return (
      <div className="space-y-3">
        <PageHeader title="Platform retention" subtitle="Governance inventory and dry-run planning" />
        <Card className="p-4 text-sm text-slate-600">
          Platform retention governance UI is disabled. Set FRONTEND_PLATFORM_RETENTION_GOVERNANCE_ENABLED.
        </Card>
      </div>
    )
  }

  if (!canRead) {
    return (
      <div className="space-y-3">
        <PageHeader title="Platform retention" subtitle="Governance inventory and dry-run planning" />
        <Card className="p-4 text-sm text-slate-600">You do not have permission to view platform retention.</Card>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="Platform retention"
        subtitle="Dry-run only governance foundation. Destructive purge is unavailable."
      />
      <div className="flex gap-2">
        <button className="rounded bg-slate-800 px-3 py-1.5 text-sm text-white" onClick={() => void load()}>
          Refresh
        </button>
      </div>
      {error ? <ErrorAlert message={error} /> : null}
      {loading && !targets ? <LoadingState label="Loading platform retention governance" /> : null}

      <div className="grid gap-3 md:grid-cols-4">
        <Summary label="Targets" value={overview.total} />
        <Summary label="Dry-run ready" value={overview.dryRunReady} />
        <Summary label="Inventory only" value={overview.inventoryOnly} />
        <Summary label="Active holds" value={overview.activeHolds} />
        <Summary label="Legal-hold supported" value={overview.legalHoldSupported} />
        <Summary label="High risk" value={overview.highRisk} />
        <Summary label="Destructive purge" value="Disabled" />
      </div>

      {plan?.warnings.length ? (
        <Card className="border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          <p className="font-semibold">Dry-run warnings</p>
          <ul className="mt-1 list-inside list-disc">
            {plan.warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </Card>
      ) : null}

      <Card className="overflow-x-auto p-0">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-3 py-2">Target</th>
              <th className="px-3 py-2">Service</th>
              <th className="px-3 py-2">Class</th>
              <th className="px-3 py-2">Retention</th>
              <th className="px-3 py-2">Hold</th>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2">Risk</th>
              <th className="px-3 py-2">Purge</th>
            </tr>
          </thead>
          <tbody>
            {(targets?.targets ?? []).map((t) => (
              <tr key={t.targetKey} className="border-b border-slate-100">
                <td className="px-3 py-2">
                  <div className="font-medium">{t.displayName}</div>
                  <div className="font-mono text-xs text-slate-500">{t.targetKey}</div>
                </td>
                <td className="px-3 py-2">{t.service}</td>
                <td className="px-3 py-2">{t.dataClass}</td>
                <td className="px-3 py-2">{t.defaultRetentionDays ?? 'Policy-defined'}</td>
                <td className="px-3 py-2">{t.legalHoldSupported ? 'Supported' : 'No'}</td>
                <td className="px-3 py-2">{t.status}</td>
                <td className="px-3 py-2">{t.riskLevel}</td>
                <td className="px-3 py-2">{t.destructivePurgeSupported ? 'Future gated' : 'Unavailable'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card className="overflow-x-auto p-0">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-3 py-2">Dry-run target</th>
              <th className="px-3 py-2">Eligible</th>
              <th className="px-3 py-2">Purgeable</th>
              <th className="px-3 py-2">Blocked</th>
              <th className="px-3 py-2">Warnings</th>
            </tr>
          </thead>
          <tbody>
            {(plan?.targets ?? []).map((t) => (
              <tr key={t.targetKey} className="border-b border-slate-100">
                <td className="px-3 py-2 font-mono text-xs">{t.targetKey}</td>
                <td className="px-3 py-2">{t.eligibleCount ?? 'Not counted'}</td>
                <td className="px-3 py-2">{t.purgeableCount}</td>
                <td className="px-3 py-2">
                  {t.blockedByLegalHold ? `Blocked (${t.activeHoldKeys.join(', ')})` : 'No'}
                </td>
                <td className="px-3 py-2 text-xs text-slate-600">{t.warnings.join(' ')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card className="space-y-3 p-4">
        <h2 className="text-base font-semibold text-slate-900">Platform legal holds</h2>
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2">Hold</th>
                <th className="px-3 py-2">Scope</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Created</th>
                <th className="px-3 py-2">Expiry</th>
              </tr>
            </thead>
            <tbody>
              {(holds?.items ?? []).map((h) => (
                <tr key={h.id} className="border-b border-slate-100">
                  <td className="px-3 py-2 font-mono text-xs">{h.holdKey}</td>
                  <td className="px-3 py-2">{h.scope}</td>
                  <td className="px-3 py-2">{h.status}</td>
                  <td className="px-3 py-2 text-xs">{h.createdAt}</td>
                  <td className="px-3 py-2 text-xs">{h.expiresAt ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {canWrite ? (
          <div className="grid gap-3 lg:grid-cols-2">
            <div className="space-y-2 rounded border border-slate-200 p-3">
              <p className="text-sm font-semibold">Create hold</p>
              <input className="w-full rounded border p-2 text-sm" placeholder="holdKey" value={holdKey} onChange={(e) => setHoldKey(e.target.value)} />
              <select className="w-full rounded border p-2 text-sm" value={scope} onChange={(e) => setScope(e.target.value)}>
                {scopeOptions.map((s) => <option key={s}>{s}</option>)}
              </select>
              <input className="w-full rounded border p-2 text-sm" placeholder="scopeRefId (optional UUID)" value={scopeRefId} onChange={(e) => setScopeRefId(e.target.value)} />
              <textarea className="w-full rounded border p-2 text-sm" rows={3} placeholder="Reason" value={reason} onChange={(e) => setReason(e.target.value)} />
              <button className="rounded bg-slate-800 px-3 py-1.5 text-sm text-white" onClick={() => void onCreate()}>Create hold</button>
            </div>
            <div className="space-y-2 rounded border border-slate-200 p-3">
              <p className="text-sm font-semibold">Release hold</p>
              <select className="w-full rounded border p-2 text-sm" value={releaseId} onChange={(e) => setReleaseId(e.target.value)}>
                <option value="">Select active hold</option>
                {(holds?.items ?? []).filter((h) => h.status === 'ACTIVE').map((h) => (
                  <option key={h.id} value={h.id}>{h.holdKey}</option>
                ))}
              </select>
              <textarea className="w-full rounded border p-2 text-sm" rows={3} placeholder="Release reason" value={releaseReason} onChange={(e) => setReleaseReason(e.target.value)} />
              <button className="rounded border border-slate-300 px-3 py-1.5 text-sm" onClick={() => void onRelease()}>Release hold</button>
            </div>
          </div>
        ) : (
          <p className="text-sm text-slate-600">Legal hold writes require retention legal-hold permission and MFA when enforced.</p>
        )}
      </Card>
    </div>
  )
}

function Summary({ label, value }: { label: string; value: number | string }) {
  return (
    <Card className="p-3">
      <div className="text-xs uppercase text-slate-500">{label}</div>
      <div className="mt-1 text-xl font-semibold text-slate-900">{value}</div>
    </Card>
  )
}

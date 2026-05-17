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
  type RetentionServiceSummary,
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

      {plan?.serviceSummaries?.length ? (
        <div className="space-y-2">
          <h2 className="text-sm font-semibold uppercase text-slate-500">Service readiness</h2>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            {plan.serviceSummaries.map((s) => (
              <ServiceReadinessCard key={s.service} summary={s} generatedAt={plan.generatedAt} />
            ))}
          </div>
        </div>
      ) : null}

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

      <div id="platform-retention-dry-run" className="scroll-mt-4">
      <Card className="overflow-x-auto p-0">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-3 py-2">Dry-run target</th>
              <th className="px-3 py-2">Status</th>
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
                <td className="px-3 py-2">
                  <StatusBadge status={t.status} />
                </td>
                <td className="px-3 py-2">{t.eligibleCount ?? 'Not counted'}</td>
                <td className="px-3 py-2">{t.purgeableCount}</td>
                <td className="px-3 py-2">
                  {t.blockedByLegalHold
                    ? `Blocked${t.activeHoldKeys?.length ? ` (${t.activeHoldKeys.join(', ')})` : ''}`
                    : 'No'}
                </td>
                <td className="px-3 py-2 text-xs text-slate-600">
                  <WarningChips warnings={t.warnings} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
      </div>

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

function StatusBadge({ status }: { status: string }) {
  const style =
    status === 'DRY_RUN_READY'
      ? 'bg-emerald-50 text-emerald-800 ring-emerald-200'
      : status === 'INVENTORY_ONLY'
        ? 'bg-slate-100 text-slate-700 ring-slate-200'
        : 'bg-amber-50 text-amber-800 ring-amber-200'
  return (
    <span className={`inline-flex rounded px-2 py-0.5 text-xs ring-1 ${style}`}>{status}</span>
  )
}

const SERVICE_STATUS_TONE: Record<string, string> = {
  READY: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  PARTIAL: 'bg-amber-50 text-amber-800 ring-amber-200',
  INVENTORY_ONLY: 'bg-slate-100 text-slate-700 ring-slate-200',
  DISABLED: 'bg-slate-100 text-slate-700 ring-slate-200',
  UNAVAILABLE: 'bg-rose-50 text-rose-800 ring-rose-200',
  BLOCKED_BY_HOLD: 'bg-rose-50 text-rose-800 ring-rose-200',
  ERROR: 'bg-rose-50 text-rose-800 ring-rose-200',
}

function ServiceStatusBadge({ status }: { status: string }) {
  const style = SERVICE_STATUS_TONE[status] ?? 'bg-slate-100 text-slate-700 ring-slate-200'
  return (
    <span className={`inline-flex rounded px-2 py-0.5 text-xs font-medium ring-1 ${style}`}>
      {status}
    </span>
  )
}

function ServiceReadinessCard({
  summary,
  generatedAt,
}: {
  summary: RetentionServiceSummary
  generatedAt: string
}) {
  return (
    <Card className="space-y-2 p-3">
      <div className="flex items-center justify-between gap-2">
        <div>
          <div className="text-sm font-semibold text-slate-900">{summary.service}</div>
          <div className="text-xs uppercase text-slate-500">{summary.dataClass}</div>
        </div>
        <ServiceStatusBadge status={summary.status} />
      </div>
      <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs text-slate-600">
        <ReadinessMetric label="Total" value={summary.totalTargets} />
        <ReadinessMetric label="Dry-run ready" value={summary.dryRunReadyTargets} />
        <ReadinessMetric label="Inventory only" value={summary.inventoryOnlyTargets} />
        <ReadinessMetric label="Blocked by hold" value={summary.blockedTargets} />
        <ReadinessMetric label="Capped" value={summary.cappedTargets} />
        <ReadinessMetric label="Warnings" value={summary.warningCount} />
      </dl>
      <div className="flex items-center justify-between text-xs text-slate-500">
        <span>{generatedAt}</span>
        <a className="text-slate-700 underline" href="#platform-retention-dry-run">
          View targets
        </a>
      </div>
    </Card>
  )
}

function ReadinessMetric({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-slate-500">{label}</dt>
      <dd className="font-semibold text-slate-900">{value}</dd>
    </div>
  )
}

const WARNING_TONE: Record<string, string> = {
  CONTENT_RETENTION_LEGAL_HOLD_BLOCKED: 'bg-rose-50 text-rose-800 ring-rose-200',
  CONTENT_RETENTION_QUERY_CAPPED: 'bg-amber-50 text-amber-800 ring-amber-200',
  CONTENT_RETENTION_SERVICE_UNAVAILABLE: 'bg-rose-50 text-rose-800 ring-rose-200',
  CONTENT_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING: 'bg-amber-50 text-amber-800 ring-amber-200',
  CONTENT_RETENTION_DRY_RUN_DISABLED: 'bg-slate-100 text-slate-700 ring-slate-200',
  CONTENT_RETENTION_TARGET_INVENTORY_ONLY: 'bg-slate-100 text-slate-700 ring-slate-200',
  NOTIFICATION_RETENTION_LEGAL_HOLD_BLOCKED: 'bg-rose-50 text-rose-800 ring-rose-200',
  NOTIFICATION_RETENTION_QUERY_CAPPED: 'bg-amber-50 text-amber-800 ring-amber-200',
  NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE: 'bg-rose-50 text-rose-800 ring-rose-200',
  NOTIFICATION_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING: 'bg-amber-50 text-amber-800 ring-amber-200',
  NOTIFICATION_RETENTION_DRY_RUN_DISABLED: 'bg-slate-100 text-slate-700 ring-slate-200',
  NOTIFICATION_RETENTION_TARGET_INVENTORY_ONLY: 'bg-slate-100 text-slate-700 ring-slate-200',
  NOTIFICATION_RETENTION_COUNT_FAILED: 'bg-rose-50 text-rose-800 ring-rose-200',
  NOTIFICATION_RETENTION_DB_PERMISSION_DENIED: 'bg-rose-50 text-rose-800 ring-rose-200',
  NOTIFICATION_RETENTION_DRY_RUN_FAILED: 'bg-rose-50 text-rose-800 ring-rose-200',
  PLATFORM_RETENTION_NOTIFICATION_PLAN_INCLUDED: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
}

function WarningChips({ warnings }: { warnings: string[] }) {
  if (!warnings || warnings.length === 0) return <span>—</span>
  return (
    <div className="flex flex-wrap gap-1">
      {warnings.map((w) => (
        <span
          key={w}
          className={`inline-flex rounded px-2 py-0.5 text-xs ring-1 ${
            WARNING_TONE[w] ?? 'bg-slate-100 text-slate-700 ring-slate-200'
          }`}
        >
          {w}
        </span>
      ))}
    </div>
  )
}

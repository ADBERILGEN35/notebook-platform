import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  isEnterpriseAdminWriteEnabled,
  isScimCompatibilityDiagnosticsUiEnabled,
  isScimDeltaProviderPocUiEnabled,
} from '../../shared/config/admin-feature-flags'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { LoadingState } from '../../shared/components/LoadingState'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { PermissionDenied } from '../../shared/components/PermissionDenied'
import { ApiError } from '../../shared/api/api-client'
import { useEnterpriseStatus } from '../../features/admin/enterprise/use-enterprise-status'
import type { EnterpriseStatusResponse, EnterpriseWarning, WarningSeverity } from '../../features/admin/enterprise/enterprise-schema'
import {
  fetchScimCompatibilityStatus,
  fetchScimDeltaReadiness,
  fetchScimSyncRuns,
  type ScimCompatibilityStatus,
  type ScimDeltaReadiness,
  type ScimSyncRunPage,
} from '../../features/admin/enterprise/scim-diagnostics-api'
import { isPwaEnabled } from '../../shared/config/offline-feature-flags'

const DOCS = {
  enterpriseConsole: 'docs/enterprise-admin-console.md',
  breakGlass: 'docs/break-glass-admin-access.md',
  sso: 'docs/enterprise-sso.md',
  scim: 'docs/scim-provisioning.md',
  siem: 'docs/siem-streaming-push.md',
  audit: 'docs/admin-audit-proxy.md',
  notifications: 'docs/notification-service.md',
  merge: 'docs/merge-observability.md',
} as const

function DocLink({ path, label }: { path: string; label: string }) {
  return (
    <p className="text-xs text-slate-500">
      <span className="font-medium text-slate-600">{label}:</span>{' '}
      <code className="rounded bg-slate-100 px-1 text-[11px]">{path}</code>
    </p>
  )
}

function severityClass(s: WarningSeverity): string {
  switch (s) {
    case 'CRITICAL':
      return 'border-red-200 bg-red-50 text-red-900'
    case 'WARNING':
      return 'border-amber-200 bg-amber-50 text-amber-900'
    default:
      return 'border-slate-200 bg-slate-50 text-slate-800'
  }
}

function WarningsPanel({ warnings }: { warnings: EnterpriseWarning[] }) {
  if (!warnings.length) {
    return (
      <Card>
        <p className="text-sm text-slate-600">No active warnings for the current configuration snapshot.</p>
      </Card>
    )
  }
  return (
    <Card className="space-y-2">
      <p className="text-xs font-semibold uppercase text-slate-500">Warnings</p>
      <ul className="space-y-2">
        {warnings.map((w) => (
          <li
            key={`${w.code}-${w.message.slice(0, 24)}`}
            className={`rounded border px-3 py-2 text-sm ${severityClass(w.severity)}`}
          >
            <span className="font-mono text-[11px]">{w.code}</span>
            <span className="mx-2 text-slate-400">·</span>
            {w.message}
            <span className="ml-2 text-[10px] uppercase opacity-80">({w.severity})</span>
          </li>
        ))}
      </ul>
    </Card>
  )
}

function Badge({ ok, label }: { ok: boolean; label: string }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium ${
        ok ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'
      }`}
    >
      {label}
    </span>
  )
}

function FeatureCard({
  title,
  enabled,
  children,
  docPath,
  docLabel,
  warnCount,
  footer,
}: {
  title: string
  enabled: boolean
  children: ReactNode
  docPath: string
  docLabel: string
  warnCount?: number
  footer?: ReactNode
}) {
  return (
    <Card className="space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
        <div className="flex flex-wrap items-center gap-2">
          <Badge ok={enabled} label={enabled ? 'Enabled' : 'Disabled'} />
          {warnCount != null && warnCount > 0 ? (
            <span className="text-[11px] font-medium text-amber-700">{warnCount} warnings</span>
          ) : null}
        </div>
      </div>
      <div className="text-xs text-slate-600">{children}</div>
      <DocLink path={docPath} label={docLabel} />
      {footer}
    </Card>
  )
}

function warningCountForCodes(warnings: EnterpriseWarning[], codes: string[]): number {
  const set = new Set(codes)
  return warnings.filter((w) => set.has(w.code)).length
}

function readinessScore(warnings: EnterpriseWarning[]): number {
  let score = 100
  for (const w of warnings) {
    if (w.severity === 'CRITICAL') score -= 15
    else if (w.severity === 'WARNING') score -= 8
    else score -= 3
  }
  return Math.max(0, Math.min(100, score))
}

function ScimDeltaReadinessCard({
  readiness,
  error,
}: {
  readiness?: ScimDeltaReadiness
  error?: unknown
}) {
  if (error) {
    return (
      <p className="mt-2 text-[11px] text-amber-700">Delta readiness unavailable or permission denied.</p>
    )
  }
  if (!readiness) return null
  return (
    <div className="mt-3 space-y-2 border-t border-slate-200 pt-3">
      <p className="font-medium text-slate-800">Provider delta readiness (POC)</p>
      <div className="grid grid-cols-2 gap-1 text-[11px] text-slate-600">
        <span>POC enabled: {readiness.deltaPocEnabled ? 'yes' : 'no'}</span>
        <span>Dry-run only: {readiness.dryRunOnly ? 'yes' : 'no'}</span>
        <span>Strategy: {readiness.selectedStrategy}</span>
        <span>Source: {readiness.deltaSource}</span>
        <span>Filtering: {readiness.supportsFiltering ? 'yes' : 'no'}</span>
        <span>Pagination: {readiness.supportsPagination ? 'yes' : 'no'}</span>
        <span>Patch: {readiness.supportsPatch ? 'yes' : 'no'}</span>
        <span>Retry-After: {readiness.supportsRetryAfter ? 'yes' : 'no'}</span>
        <span>Capability aligned: {readiness.capabilityAligned ? 'yes' : 'no'}</span>
        <span>Remote fetch: {readiness.remoteFetchEnabled ? 'enabled' : 'disabled'}</span>
        <span>Remote configured: {readiness.remoteFetchConfigured ? 'yes' : 'no'}</span>
        <span>Remote attempted: {readiness.remoteFetchAttempted ? 'yes' : 'no'}</span>
        <span>Fetched count: {readiness.fetchedResourceCount}</span>
        <span>Next cursor: {readiness.nextCursorPresent ? 'present' : 'none'}</span>
        <span>HTTP timeout: {readiness.httpTimeoutMs}ms</span>
        <span>Backoff base: {readiness.backoffBaseSeconds}s</span>
        <span>Provider error: {readiness.providerErrorClass}</span>
        <span>Retry-After observed: {readiness.retryAfterObserved ? 'yes' : 'no'}</span>
        <span>Retry-After seconds: {readiness.retryAfterSeconds ?? 'n/a'}</span>
        <span>Last dry-run: {readiness.lastDryRunStatus}</span>
      </div>
      <p className="text-[11px] text-slate-600">{readiness.deprovisionSemantics}</p>
      {readiness.warnings.length ? (
        <p className="text-[11px] text-amber-700">Warnings: {readiness.warnings.join(', ')}</p>
      ) : null}
    </div>
  )
}

function EnterpriseContent({
  data,
  mode,
  scimDiagnostics,
  scimRuns,
  scimDeltaReadiness,
  scimDiagnosticsError,
  scimDeltaReadinessError,
}: {
  data: EnterpriseStatusResponse
  mode: 'overview' | 'security' | 'integrations'
  scimDiagnostics?: ScimCompatibilityStatus
  scimRuns?: ScimSyncRunPage
  scimDeltaReadiness?: ScimDeltaReadiness
  scimDiagnosticsError?: unknown
  scimDeltaReadinessError?: unknown
}) {
  const { features, warnings } = data
  const w = warnings
  const ssoW = warningCountForCodes(w, ['SSO_ADMIN_MAPPING_MISSING', 'SSO_NO_PROVIDERS'])
  const scimW = warningCountForCodes(w, ['SCIM_TOKEN_MISSING'])
  const siemW = warningCountForCodes(w, ['SIEM_WORKER_DISABLED', 'SIEM_SECRET_NOT_CONFIGURED'])
  const mfaW = warningCountForCodes(w, ['ADMIN_MFA_MODE_NOT_ENFORCE'])
  const auditW = warningCountForCodes(w, [
    'AUDIT_EXPORT_MACHINE_AUTH_DISABLED',
    'AUDIT_SCHEDULED_EXPORT_NOT_FLAGGED',
    'AUDIT_ARCHIVE_UPLOAD_DISABLED',
  ])
  const notifW = warningCountForCodes(w, [
    'NOTIFICATION_SSE_WITHOUT_DISTRIBUTED_FANOUT',
    'NOTIFICATION_DIGEST_WORKER_DISABLED',
  ])
  const mergeW = warningCountForCodes(w, ['MERGE_APPLY_DISABLED', 'MERGE_AUDIT_FAILURES_DISABLED'])

  const show = {
    overview: mode === 'overview',
    security: mode === 'security',
    integrations: mode === 'integrations',
  }

  return (
    <div className="space-y-4">
      {show.overview ? (
        <Card>
          <p className="text-xs font-semibold uppercase text-slate-500">Readiness snapshot</p>
          <p className="mt-1 text-2xl font-semibold text-slate-900">{readinessScore(w)}</p>
          <p className="text-xs text-slate-500">
            Heuristic score from gateway warnings (not a security certification). Partial data lowers trust — see
            warnings.
          </p>
          {(data.identityUnavailable || data.notificationUnavailable || data.contentUnavailable) && (
            <p className="mt-2 text-xs text-amber-800">
              Partial status:{' '}
              {data.identityUnavailable ? 'identity-service status unavailable. ' : ''}
              {data.notificationUnavailable ? 'notification-service status unavailable.' : ''}
              {data.contentUnavailable ? 'content-service status unavailable.' : ''}
            </p>
          )}
        </Card>
      ) : null}

      {show.overview ? <WarningsPanel warnings={w} /> : null}

      <div className="grid gap-3 md:grid-cols-2">
        {(show.overview || show.security) && features.sso ? (
          <FeatureCard
            title="SSO"
            enabled={features.sso.enabled}
            docPath={DOCS.sso}
            docLabel="SSO docs"
            warnCount={ssoW}
          >
            <ul className="list-inside list-disc space-y-0.5">
              <li>Providers configured: {features.sso.providersConfigured}</li>
              <li>Allowed domains: {features.sso.allowedDomainsConfigured ? 'yes' : 'no'}</li>
              <li>Admin group mapping: {features.sso.adminGroupMappingConfigured ? 'yes' : 'no'}</li>
              <li>Trust IdP MFA: {features.sso.trustIdpMfa ? 'yes' : 'no'}</li>
            </ul>
          </FeatureCard>
        ) : null}

        {(show.overview || show.security) && features.scim ? (
          <FeatureCard
            title="SCIM"
            enabled={features.scim.enabled}
            docPath={DOCS.scim}
            docLabel="SCIM docs"
            warnCount={scimW}
          >
            <ul className="list-inside list-disc space-y-0.5">
              <li>Groups: {features.scim.groupsEnabled ? 'on' : 'off'}</li>
              <li>Admin groups: {features.scim.adminGroupsConfigured ? 'configured' : 'not configured'}</li>
              <li>Token: {features.scim.tokenConfigured ? 'configured' : 'not configured'}</li>
              <li>Provider: {features.scim.providerType ?? 'generic'}</li>
              <li>Delta sync: {features.scim.deltaSyncMode ?? 'disabled'}</li>
            </ul>
            {scimDiagnostics ? (
              <div className="mt-3 space-y-3 border-t border-slate-200 pt-3">
                <div>
                  <p className="font-medium text-slate-800">Provider compatibility</p>
                  <div className="mt-1 grid grid-cols-2 gap-1 text-[11px] text-slate-600">
                    <span>Bulk: {scimDiagnostics.scimProvider.bulkSupported ? 'yes' : 'no'}</span>
                    <span>Filtering: {scimDiagnostics.scimProvider.filteringSupported ? 'yes' : 'no'}</span>
                    <span>Patch: {scimDiagnostics.scimProvider.patchSupported ? 'yes' : 'no'}</span>
                    <span>Nested groups: {scimDiagnostics.scimProvider.nestedGroupsSupported ? 'yes' : 'no'}</span>
                    <span>Rate-limit aware: {scimDiagnostics.scimProvider.rateLimitAware ? 'yes' : 'no'}</span>
                    <span>Max page: {scimDiagnostics.scimProvider.maxPageSize}</span>
                  </div>
                  {scimDiagnostics.scimProvider.warnings.length ? (
                    <p className="mt-1 text-[11px] text-amber-700">
                      Warnings: {scimDiagnostics.scimProvider.warnings.join(', ')}
                    </p>
                  ) : null}
                </div>
                <div>
                  <p className="font-medium text-slate-800">Checkpoints</p>
                  {scimDiagnostics.checkpoints.length ? (
                    <ul className="mt-1 space-y-1 text-[11px] text-slate-600">
                      {scimDiagnostics.checkpoints.map((c) => (
                        <li key={c.id}>
                          {c.resourceType}: {c.status} · {c.syncMode} · last success {c.lastSuccessfulSyncAt ?? '-'}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="mt-1 text-[11px] text-slate-500">No checkpoints yet.</p>
                  )}
                </div>
                <div>
                  <p className="font-medium text-slate-800">Sync runs</p>
                  {(scimRuns?.items ?? []).length ? (
                    <div className="mt-1 overflow-x-auto">
                      <table className="min-w-full text-[11px]">
                        <thead className="text-left text-slate-500">
                          <tr>
                            <th className="pr-2">Provider</th>
                            <th className="pr-2">Resource</th>
                            <th className="pr-2">Mode</th>
                            <th className="pr-2">Status</th>
                            <th className="pr-2">Processed</th>
                            <th className="pr-2">Errors</th>
                          </tr>
                        </thead>
                        <tbody>
                          {(scimRuns?.items ?? []).map((run) => (
                            <tr key={run.id} className="border-t border-slate-100">
                              <td className="pr-2">{run.provider}</td>
                              <td className="pr-2">{run.resourceType}</td>
                              <td className="pr-2">{run.syncMode}</td>
                              <td className="pr-2">{run.status}</td>
                              <td className="pr-2">{run.processedCount}</td>
                              <td className="pr-2">{run.errorCount}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <p className="mt-1 text-[11px] text-slate-500">No sync runs recorded.</p>
                  )}
                </div>
              </div>
            ) : scimDiagnosticsError ? (
              <p className="mt-2 text-[11px] text-amber-700">SCIM diagnostics unavailable or permission denied.</p>
            ) : null}
            <ScimDeltaReadinessCard readiness={scimDeltaReadiness} error={scimDeltaReadinessError} />
          </FeatureCard>
        ) : null}

        {(show.overview || show.security) && features.mfa ? (
          <FeatureCard
            title="MFA"
            enabled={features.mfa.identityMfaEnabled}
            docPath={DOCS.enterpriseConsole}
            docLabel="Console docs"
            warnCount={mfaW}
            footer={
              isEnterpriseAdminWriteEnabled() && features.mfa.adminMfaMode !== 'enforce' ? (
                <p className="text-xs text-slate-600">
                  <Link
                    className="text-primary-700 underline"
                    to="/app/admin/enterprise/change-requests?op=ADMIN_MFA_MODE_UPDATE&val=enforce"
                  >
                    Request change: enforce admin MFA mode
                  </Link>
                </p>
              ) : null
            }
          >
            <ul className="list-inside list-disc space-y-0.5">
              <li>Admin MFA mode (gateway): {features.mfa.adminMfaMode}</li>
              <li>Accepted methods: {features.mfa.acceptedMethods.join(', ') || '—'}</li>
              <li>Identity MFA: {features.mfa.identityMfaEnabled ? 'on' : 'off'}</li>
              <li>WebAuthn: {features.mfa.webauthnEnabled ? 'on' : 'off'}</li>
            </ul>
          </FeatureCard>
        ) : null}

        {(show.overview || show.security) && features.breakGlass ? (
          <FeatureCard
            title="Break-glass admin access"
            enabled={features.breakGlass.enabled && features.breakGlass.gatewayAllowed}
            docPath={DOCS.breakGlass}
            docLabel="Break-glass docs"
            warnCount={0}
            footer={
              <p className="text-xs text-amber-900">
                Disabled by default. No secrets are displayed. Admin write is blocked unless explicitly enabled.
              </p>
            }
          >
            <ul className="list-inside list-disc space-y-0.5">
              <li>Identity enabled: {features.breakGlass.enabled ? 'yes' : 'no'}</li>
              <li>Credential mode: {features.breakGlass.credentialMode}</li>
              <li>Allowed modes: {features.breakGlass.allowedModes.join(', ') || '—'}</li>
              <li>Static token configured (hash only): {features.breakGlass.staticTokenConfigured ? 'yes' : 'no'}</li>
              <li>WebAuthn enabled: {features.breakGlass.webauthnEnabled ? 'yes' : 'no'}</li>
              <li>WebAuthn credential count: {features.breakGlass.webauthnCredentialCount}</li>
              <li>Offline signed enabled: {features.breakGlass.offlineSignedEnabled ? 'yes' : 'no'}</li>
              <li>Offline public key configured: {features.breakGlass.offlinePublicKeyConfigured ? 'yes' : 'no'}</li>
              <li>Static token rotation recommended: {features.breakGlass.rotationRecommended ? 'yes' : 'no'}</li>
              <li>Last static token use: {features.breakGlass.lastStaticTokenUsedAt || '—'}</li>
              <li>Approval mode: {features.breakGlass.approvalMode}</li>
              <li>Pending reviews: {features.breakGlass.pendingReviewCount}</li>
              <li>Overdue reviews: {features.breakGlass.overdueReviewCount}</li>
              <li>Gateway allowed: {features.breakGlass.gatewayAllowed ? 'yes' : 'no'}</li>
              <li>Admin write allowed: {features.breakGlass.adminWriteAllowed ? 'yes' : 'no'}</li>
              <li>Session TTL: {features.breakGlass.sessionTtlMinutes} min</li>
              <li>Max active sessions: {features.breakGlass.maxActiveSessions}</li>
              <li>Reason required: {features.breakGlass.requireReason ? 'yes' : 'no'}</li>
              <li>Require MFA: {features.breakGlass.requireMfa ? 'yes' : 'no'}</li>
            </ul>
          </FeatureCard>
        ) : null}

        {(show.overview || show.security) && features.siem ? (
          <FeatureCard
            title="SIEM"
            enabled={features.siem.enabled}
            docPath={DOCS.siem}
            docLabel="SIEM docs"
            warnCount={siemW}
          >
            <ul className="list-inside list-disc space-y-0.5">
              <li>Provider: {features.siem.provider}</li>
              <li>Worker: {features.siem.workerEnabled ? 'enabled' : 'disabled'}</li>
              <li>Endpoint: {features.siem.endpointConfigured ? 'configured' : 'not configured'}</li>
              <li>Outbound auth secret: {features.siem.secretConfigured ? 'configured' : 'not configured'}</li>
            </ul>
          </FeatureCard>
        ) : null}

        {(show.overview || show.security) && features.auditExport ? (
          <FeatureCard
            title="Audit export & archive"
            enabled={features.auditExport.enabled}
            docPath={DOCS.audit}
            docLabel="Audit proxy / export"
            warnCount={auditW}
          >
            <ul className="list-inside list-disc space-y-0.5">
              <li>Machine auth: {features.auditExport.machineAuthEnabled ? 'enabled' : 'disabled'}</li>
              <li>Machine JWT verification key: {features.auditExport.machineAuthPublicKeyConfigured ? 'configured' : 'not configured'}</li>
              <li>Scheduled export (flagged): {features.auditExport.scheduledExportConfigured ? 'yes' : 'no'}</li>
              <li>Archive upload (flagged): {features.auditExport.archiveUploadEnabled ? 'yes' : 'no'}</li>
              <li>Archive provider: {features.auditExport.archiveProvider || '—'}</li>
            </ul>
          </FeatureCard>
        ) : null}

        {(show.overview || show.security) && features.gatewaySecurity ? (
          <FeatureCard
            title="Gateway security"
            enabled={features.gatewaySecurity.adminEnabled}
            docPath={DOCS.enterpriseConsole}
            docLabel="Console docs"
          >
            <ul className="list-inside list-disc space-y-0.5">
              <li>Admin audit UI: {features.gatewaySecurity.adminAuditEnabled ? 'enabled' : 'disabled'}</li>
              <li>Rate limiting: {features.gatewaySecurity.rateLimitEnabled ? 'on' : 'off'}</li>
              <li>CSRF (cookie transports): {features.gatewaySecurity.csrfEnabled ? 'relevant' : 'n/a'}</li>
              <li>Auth transport: {features.gatewaySecurity.authTransport}</li>
              <li>Cookie mode: {features.gatewaySecurity.cookieModeEnabled ? 'enabled' : 'disabled'}</li>
            </ul>
          </FeatureCard>
        ) : null}

        {(show.overview || show.integrations) && features.notifications ? (
          <FeatureCard
            title="Notifications"
            enabled={features.notifications.sseEnabled}
            docPath={DOCS.notifications}
            docLabel="Notification service"
            warnCount={notifW}
          >
            <ul className="list-inside list-disc space-y-0.5">
              <li>SSE: {features.notifications.sseEnabled ? 'on' : 'off'}</li>
              <li>Distributed fan-out: {features.notifications.distributedFanoutEnabled ? 'on' : 'off'}</li>
              <li>Digest: {features.notifications.digestEnabled ? 'on' : 'off'}</li>
              <li>Digest worker: {features.notifications.digestWorkerEnabled ? 'on' : 'off'}</li>
            </ul>
          </FeatureCard>
        ) : null}

        {(show.overview || show.integrations) && features.mergeResolution ? (
          <FeatureCard
            title="Merge / conflict resolution"
            enabled={features.mergeResolution.analysisEnabled}
            docPath={DOCS.merge}
            docLabel="Merge observability"
            warnCount={mergeW}
            footer={
              isEnterpriseAdminWriteEnabled() ? (
                <div className="space-y-1 text-xs text-slate-600">
                  {!features.mergeResolution.applyEnabled ? (
                    <p>
                      <Link
                        className="text-primary-700 underline"
                        to="/app/admin/enterprise/change-requests?op=MERGE_APPLY_ROLLOUT_REQUEST&val=true"
                      >
                        Request enable merge apply
                      </Link>
                    </p>
                  ) : null}
                  {!features.mergeResolution.analysisEnabled ? (
                    <p>
                      <Link
                        className="text-primary-700 underline"
                        to="/app/admin/enterprise/change-requests?op=MERGE_ANALYSIS_ROLLOUT_REQUEST&val=true"
                      >
                        Request enable merge analysis
                      </Link>
                    </p>
                  ) : null}
                </div>
              ) : null
            }
          >
            <ul className="list-inside list-disc space-y-0.5">
              <li>Backend analyze: {features.mergeResolution.analysisEnabled ? 'enabled' : 'disabled'}</li>
              <li>Backend apply: {features.mergeResolution.applyEnabled ? 'enabled' : 'disabled'}</li>
              <li>Supported versions: {features.mergeResolution.supportedVersions.join(', ') || '—'}</li>
              <li>Idempotency: {features.mergeResolution.idempotencyEnabled ? 'enabled' : 'disabled'}</li>
              <li>Metrics: {features.mergeResolution.metricsEnabled ? 'enabled' : 'disabled'}</li>
              <li>Failure audit events: {features.mergeResolution.auditFailuresEnabled ? 'enabled' : 'disabled'}</li>
            </ul>
          </FeatureCard>
        ) : null}

        {show.overview ? (
          <Card className="space-y-2">
            <h3 className="text-sm font-semibold text-slate-900">PWA / offline (client)</h3>
            <Badge ok={isPwaEnabled()} label={isPwaEnabled() ? 'PWA enabled' : 'PWA disabled'} />
            <p className="text-xs text-slate-600">Reflects frontend build flags only; not a server readiness signal.</p>
          </Card>
        ) : null}
      </div>

      {show.overview ? (
        <Card className="space-y-2">
          <p className="text-xs font-semibold uppercase text-slate-500">Documentation</p>
          <DocLink path={DOCS.enterpriseConsole} label="Enterprise admin console" />
          <DocLink path={DOCS.audit} label="Admin audit" />
          <DocLink path={DOCS.notifications} label="Notifications" />
        </Card>
      ) : null}

      {!show.overview ? <WarningsPanel warnings={w} /> : null}
    </div>
  )
}

function EnterprisePageShell({ mode }: { mode: 'overview' | 'security' | 'integrations' }) {
  const q = useEnterpriseStatus()
  const scimDiagnosticsEnabled = isScimCompatibilityDiagnosticsUiEnabled()
  const scimDeltaPocEnabled = isScimDeltaProviderPocUiEnabled()
  const scimDiagnosticsQuery = useQuery({
    queryKey: ['admin', 'scim-compatibility'],
    queryFn: fetchScimCompatibilityStatus,
    enabled: scimDiagnosticsEnabled && mode === 'security',
    retry: false,
  })
  const scimRunsQuery = useQuery({
    queryKey: ['admin', 'scim-sync-runs'],
    queryFn: fetchScimSyncRuns,
    enabled: scimDiagnosticsEnabled && mode === 'security',
    retry: false,
  })
  const scimDeltaReadinessQuery = useQuery({
    queryKey: ['admin', 'scim-delta-readiness'],
    queryFn: fetchScimDeltaReadiness,
    enabled: scimDeltaPocEnabled && mode === 'security',
    retry: false,
  })
  const err = q.error instanceof ApiError ? q.error : null
  const showMfa = err?.errorCode === 'ADMIN_MFA_REQUIRED'
  const denied = err?.errorCode === 'ADMIN_ACCESS_DENIED'
  const disabled = err?.errorCode === 'ADMIN_ENTERPRISE_DISABLED'

  const titles = {
    overview: 'Enterprise console',
    security: 'Enterprise security',
    integrations: 'Enterprise integrations',
  } as const

  const subs = {
    overview: 'Read-only status for SSO, SCIM, SIEM, MFA, audit export, notifications, and gateway posture.',
    security: 'SSO, SCIM, MFA, SIEM, audit export, and gateway security summary.',
    integrations: 'Notification realtime/digest status and related documentation.',
  } as const

  return (
    <div className="space-y-4">
      <PageHeader title={titles[mode]} subtitle={subs[mode]} />
      <div className="flex flex-wrap gap-3 text-xs text-slate-500">
        <Link to="/app/admin" className="text-primary-600 hover:underline">
          Admin home
        </Link>
        <Link to="/app/admin/audit" className="text-primary-600 hover:underline">
          Audit events
        </Link>
      </div>

      {q.isLoading ? <LoadingState /> : null}

      {denied ? (
        <PermissionDenied
          title="Enterprise console restricted"
          message="Your account is not authorized for this admin endpoint."
        />
      ) : null}

      {showMfa ? (
        <Card>
          <p className="text-sm font-medium text-slate-900">Admin access requires multi-factor authentication.</p>
          <p className="mt-1 text-sm text-slate-600">Complete MFA verification to load enterprise status.</p>
          <Link className="mt-2 inline-block text-sm text-primary-600 hover:underline" to="/app/settings/security">
            Go to Security Settings
          </Link>
        </Card>
      ) : null}

      {disabled ? (
        <Card>
          <p className="text-sm text-slate-700">
            Enterprise admin console is disabled on the API gateway (<code className="text-xs">GATEWAY_ADMIN_ENTERPRISE_ENABLED</code>
            ).
          </p>
        </Card>
      ) : null}

      {q.isError && !showMfa && !denied && !disabled ? <ErrorAlert error={q.error} /> : null}

      {q.data ? (
        <EnterpriseContent
          data={q.data}
          mode={mode}
          scimDiagnostics={scimDiagnosticsQuery.data}
          scimRuns={scimRunsQuery.data}
          scimDeltaReadiness={scimDeltaReadinessQuery.data}
          scimDiagnosticsError={scimDiagnosticsQuery.error ?? scimRunsQuery.error}
          scimDeltaReadinessError={scimDeltaReadinessQuery.error}
        />
      ) : null}
    </div>
  )
}

export function AdminEnterpriseOverviewPage() {
  return <EnterprisePageShell mode="overview" />
}

export function AdminEnterpriseSecurityPage() {
  return <EnterprisePageShell mode="security" />
}

export function AdminEnterpriseIntegrationsPage() {
  return <EnterprisePageShell mode="integrations" />
}

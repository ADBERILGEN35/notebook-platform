import { useCallback, useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { Button } from '../../shared/components/Button'
import { Input } from '../../shared/components/Input'
import { LoadingState } from '../../shared/components/LoadingState'
import { EmptyState } from '../../shared/components/EmptyState'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { PaginationControls } from '../../shared/components/PaginationControls'
import { PermissionDenied } from '../../shared/components/PermissionDenied'
import type { AuditEvent, AuditQueryFilters, AuditSource } from '../../features/admin/audit/types'
import { auditFiltersFromUrlParams, auditFiltersToUrlParams } from '../../features/admin/audit/audit-url-state'
import {
  auditQueryFiltersSchema,
  defaultAuditFilters,
} from '../../features/admin/audit/audit-schema'
import { exportAuditEvents, queryAuditEvents } from '../../features/admin/audit/audit-api'
import { findMockAuditEventById } from '../../features/admin/audit/audit-mock-api'
import { maskSensitiveMetadata } from '../../features/admin/audit/metadata-mask'
import { ApiError } from '../../shared/api/api-client'
import { useMediaQuery } from '../../shared/hooks/useMediaQuery'
import { useAuthStore } from '../../features/auth/auth-store'
import { PERM_AUDIT_EXPORT, hasPlatformPermission } from '../../features/admin/access/admin-permissions'
import { getAuditApiMode, isAdminUiDevOpen } from '../../shared/config/admin-feature-flags'

type FilterDraftFields = {
  eventType: string
  actorUserId: string
  workspaceId: string
  aggregateType: string
  aggregateId: string
  requestId: string
  createdFrom: string
  createdTo: string
}

function filtersToDraft(filters: AuditQueryFilters): FilterDraftFields {
  return {
    eventType: filters.eventType ?? '',
    actorUserId: filters.actorUserId ?? '',
    workspaceId: filters.workspaceId ?? '',
    aggregateType: filters.aggregateType ?? '',
    aggregateId: filters.aggregateId ?? '',
    requestId: filters.requestId ?? '',
    createdFrom: filters.createdFrom ?? '',
    createdTo: filters.createdTo ?? '',
  }
}

function navigateWithFilters(
  navigate: ReturnType<typeof useNavigate>,
  filters: AuditQueryFilters,
  eventId?: string,
) {
  const qs = auditFiltersToUrlParams(filters).toString()
  if (eventId) {
    navigate({ pathname: `/app/admin/audit/${eventId}`, search: qs ? `?${qs}` : '' })
    return
  }
  navigate({ pathname: `/app/admin/audit`, search: qs ? `?${qs}` : '' })
}

async function copyText(value: string) {
  try {
    await navigator.clipboard.writeText(value)
  } catch {
    window.prompt('Copy value', value)
  }
}

export function AdminAuditPage() {
  const navigate = useNavigate()
  const { eventId } = useParams<{ eventId: string }>()
  const [searchParams] = useSearchParams()
  const user = useAuthStore((s) => s.user)
  const devAuditOpen = isAdminUiDevOpen() && getAuditApiMode() === 'mock'
  const canExportAudit = devAuditOpen || hasPlatformPermission(user, PERM_AUDIT_EXPORT)

  const { filters: parsedFromUrl, parseWarning } = useMemo(
    () => auditFiltersFromUrlParams(searchParams),
    [searchParams],
  )

  const urlSyncKey = useMemo(
    () => auditFiltersToUrlParams(parsedFromUrl).toString(),
    [parsedFromUrl],
  )

  const [lastSyncedKey, setLastSyncedKey] = useState(
    () => auditFiltersToUrlParams(parsedFromUrl).toString(),
  )

  const [draft, setDraft] = useState<FilterDraftFields>(() => filtersToDraft(parsedFromUrl))
  const [formError, setFormError] = useState<string | null>(null)
  const [exportFormat, setExportFormat] = useState<'csv' | 'jsonl'>('csv')
  const [exportError, setExportError] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)
  const [showFilters, setShowFilters] = useState(false)
  const isMobile = useMediaQuery('(max-width: 639px)')

  if (urlSyncKey !== lastSyncedKey) {
    setLastSyncedKey(urlSyncKey)
    setDraft(filtersToDraft(parsedFromUrl))
  }

  const auditQuery = useQuery({
    queryKey: ['admin-audit', parsedFromUrl, getAuditApiMode()],
    queryFn: () => queryAuditEvents(parsedFromUrl),
  })

  const selectedDetail: AuditEvent | null | undefined = useMemo(() => {
    if (!eventId) return null
    const fromPage = auditQuery.data?.items.find((e) => e.id === eventId)
    if (fromPage) return fromPage
    if (getAuditApiMode() === 'mock') return findMockAuditEventById(eventId)
    return null
  }, [eventId, auditQuery.data?.items])

  const mergeDraftIntoFilters = (): AuditQueryFilters | null => {
    const merged = {
      ...parsedFromUrl,
      eventType: draft.eventType.trim() || null,
      actorUserId: draft.actorUserId.trim() || null,
      workspaceId: draft.workspaceId.trim() || null,
      aggregateType: draft.aggregateType.trim() || null,
      aggregateId: draft.aggregateId.trim() || null,
      requestId: draft.requestId.trim() || null,
      createdFrom: draft.createdFrom.trim() || null,
      createdTo: draft.createdTo.trim() || null,
    }
    const parsed = auditQueryFiltersSchema.safeParse(merged)
    if (!parsed.success) {
      setFormError(parsed.error.issues.map((i) => i.message).join('; '))
      return null
    }
    setFormError(null)
    return parsed.data
  }

  const handleApplyFilters = () => {
    const next = mergeDraftIntoFilters()
    if (!next) return
    navigateWithFilters(navigate, { ...next, page: 0 }, undefined)
  }

  const handleClearFilters = () => {
    const next = defaultAuditFilters(parsedFromUrl.source)
    navigateWithFilters(navigate, next)
  }

  const setSource = useCallback(
    (source: AuditSource) => {
      navigateWithFilters(navigate, { ...parsedFromUrl, source, page: 0 })
    },
    [navigate, parsedFromUrl],
  )

  const setPage = useCallback(
    (page: number) => {
      navigateWithFilters(navigate, { ...parsedFromUrl, page })
    },
    [navigate, parsedFromUrl],
  )

  const setSize = useCallback(
    (size: number) => {
      navigateWithFilters(navigate, { ...parsedFromUrl, page: 0, size })
    },
    [navigate, parsedFromUrl],
  )

  const setSort = useCallback(
    (sort: string) => {
      navigateWithFilters(navigate, { ...parsedFromUrl, sort, page: 0 })
    },
    [navigate, parsedFromUrl],
  )

  const openDetail = (row: AuditEvent) => {
    navigateWithFilters(navigate, parsedFromUrl, row.id)
  }

  const closeDetail = () => {
    navigateWithFilters(navigate, parsedFromUrl)
  }

  const handleExport = async () => {
    const createdFrom = draft.createdFrom.trim()
    const createdTo = draft.createdTo.trim()
    if (!createdFrom || !createdTo) {
      setExportError('Export requires createdFrom and createdTo filters.')
      return
    }
    setExportError(null)
    setExporting(true)
    try {
      const payload = await exportAuditEvents(parsedFromUrl, exportFormat)
      const objectUrl = URL.createObjectURL(payload.blob)
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = payload.fileName
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(objectUrl)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Export failed'
      setExportError(message)
    } finally {
      setExporting(false)
    }
  }

  const data = auditQuery.data
  const queryError = auditQuery.error instanceof ApiError ? auditQuery.error : null
  const showPermissionDenied =
    queryError?.errorCode === 'ADMIN_ACCESS_DENIED' ||
    queryError?.errorCode === 'ADMIN_PERMISSION_REQUIRED'
  const showMfaRequired = queryError?.errorCode === 'ADMIN_MFA_REQUIRED'
  const showAdminDisabled = queryError?.errorCode === 'ADMIN_AUDIT_DISABLED'
  const showSourceUnavailable = queryError?.errorCode === 'AUDIT_SOURCE_UNAVAILABLE'

  return (
    <div className="space-y-4">
      <PageHeader
        title="Audit Events"
        subtitle="Operational audit viewer (mock mode dev default; proxy required for production)."
      />

      <div className="flex gap-4 text-xs text-slate-500">
        <Link to="/app/admin" className="text-primary-600 hover:underline">
          Back to Admin
        </Link>
        <span>
          Audit API mode: <strong>{getAuditApiMode()}</strong>
        </span>
      </div>
      <Card>
        <p className="text-xs text-slate-600">
          Scheduled exports are configured by operations. Use this screen for ad-hoc export only.
        </p>
      </Card>

      {parseWarning ? (
        <p className="text-xs text-amber-700">{parseWarning}: using safe defaults.</p>
      ) : null}

      <Card className="space-y-3">
        <p className="text-xs font-semibold uppercase text-slate-500">Source</p>
        <div className="flex flex-wrap gap-2">
          {(['identity', 'workspace', 'content'] satisfies AuditSource[]).map((s) => (
            <Button
              key={s}
              className={`text-xs ${parsedFromUrl.source === s ? 'bg-primary-600 text-white hover:bg-primary-700' : ''}`}
              onClick={() => setSource(s)}
            >
              {s}
            </Button>
          ))}
        </div>
      </Card>

      <Card className="space-y-3">
        <p className="text-xs font-semibold uppercase text-slate-500">Filters</p>
        {isMobile ? (
          <Button type="button" className="text-xs" onClick={() => setShowFilters((v) => !v)}>
            {showFilters ? 'Hide filters' : 'Show filters'}
          </Button>
        ) : null}
        <div className={`grid gap-3 md:grid-cols-2 ${isMobile && !showFilters ? 'hidden' : ''}`}>
          <Input
            placeholder="eventType (exact)"
            value={draft.eventType}
            onChange={(event) => setDraft((prev) => ({ ...prev, eventType: event.target.value }))}
          />
          <Input
            placeholder="actorUserId (UUID)"
            value={draft.actorUserId}
            onChange={(event) => setDraft((prev) => ({ ...prev, actorUserId: event.target.value }))}
          />
          <Input
            placeholder="workspaceId (UUID)"
            value={draft.workspaceId}
            onChange={(event) => setDraft((prev) => ({ ...prev, workspaceId: event.target.value }))}
          />
          <Input
            placeholder="aggregateType"
            value={draft.aggregateType}
            onChange={(event) => setDraft((prev) => ({ ...prev, aggregateType: event.target.value }))}
          />
          <Input
            placeholder="aggregateId (UUID)"
            value={draft.aggregateId}
            onChange={(event) => setDraft((prev) => ({ ...prev, aggregateId: event.target.value }))}
          />
          <Input
            placeholder="requestId"
            value={draft.requestId}
            onChange={(event) => setDraft((prev) => ({ ...prev, requestId: event.target.value }))}
          />
          <Input
            placeholder="createdFrom (ISO8601)"
            value={draft.createdFrom}
            onChange={(event) => setDraft((prev) => ({ ...prev, createdFrom: event.target.value }))}
          />
          <Input
            placeholder="createdTo (ISO8601)"
            value={draft.createdTo}
            onChange={(event) => setDraft((prev) => ({ ...prev, createdTo: event.target.value }))}
          />
        </div>
        {formError ? <ErrorAlert error={new Error(formError)} /> : null}
        <div className="flex gap-2">
          <Button
            className="bg-primary-600 text-white hover:bg-primary-700"
            type="button"
            onClick={handleApplyFilters}
          >
            Apply filters
          </Button>
          <Button type="button" className="text-xs" onClick={handleClearFilters}>
            Clear filters
          </Button>
        </div>
      </Card>

      <Card className="space-y-2">
        <div className="flex flex-wrap items-center gap-3">
          <label className="flex items-center gap-2 text-sm text-slate-600">
            Page size:
            <select
              value={parsedFromUrl.size}
              onChange={(e) => setSize(Number(e.target.value))}
              className="rounded border border-slate-200 px-2 py-1 text-sm"
            >
              {[25, 50, 100, 200].map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            Sort:
            <select
              value={parsedFromUrl.sort}
              onChange={(e) => setSort(e.target.value)}
              className="rounded border border-slate-200 px-2 py-1 text-sm"
            >
              <option value="createdAt,desc">createdAt descending</option>
              <option value="createdAt,asc">createdAt ascending</option>
              <option value="eventType,desc">eventType descending</option>
              <option value="eventType,asc">eventType ascending</option>
              <option value="aggregateType,desc">aggregateType descending</option>
              <option value="aggregateType,asc">aggregateType ascending</option>
            </select>
          </label>
          {data ? (
            <span className="text-xs text-slate-500">
              Total: {data.totalElements} · Page {parsedFromUrl.page + 1}
            </span>
          ) : null}
          {canExportAudit ? (
            <>
              <label className="flex items-center gap-2 text-sm text-slate-600">
                Export format:
                <select
                  value={exportFormat}
                  onChange={(e) => setExportFormat(e.target.value as 'csv' | 'jsonl')}
                  className="rounded border border-slate-200 px-2 py-1 text-sm"
                >
                  <option value="csv">CSV</option>
                  <option value="jsonl">JSONL</option>
                </select>
              </label>
              <Button
                type="button"
                className="text-xs"
                onClick={handleExport}
                disabled={exporting}
                aria-label="Export audit events"
              >
                {exporting ? 'Exporting...' : 'Export'}
              </Button>
            </>
          ) : (
            <span className="text-xs text-slate-500">Export requires admin:audit:export.</span>
          )}
        </div>
        {exportError ? <ErrorAlert message={exportError} /> : null}
      </Card>

      {auditQuery.isLoading ? <LoadingState /> : null}
      {showPermissionDenied ? (
        <PermissionDenied
          title="Audit access denied"
          message="Your account is authenticated but not authorized for admin audit access."
        />
      ) : null}
      {showMfaRequired ? (
        <Card>
          <p className="text-sm font-medium text-slate-900">Admin access requires multi-factor authentication.</p>
          <p className="mt-1 text-sm text-slate-600">
            Complete MFA setup or verification to continue with admin audit features.
          </p>
          <Link className="mt-2 inline-block text-sm text-primary-600 hover:underline" to="/app/settings">
            Go to Security Settings
          </Link>
        </Card>
      ) : null}
      {showAdminDisabled ? (
        <Card>
          <p className="text-sm text-slate-700">
            Admin audit endpoint is disabled on gateway (`ADMIN_AUDIT_DISABLED`).
          </p>
        </Card>
      ) : null}
      {showSourceUnavailable ? (
        <Card>
          <p className="text-sm text-amber-700">
            Selected audit source is unavailable. Try another source or retry later.
          </p>
        </Card>
      ) : null}
      {auditQuery.isError && !showPermissionDenied && !showMfaRequired && !showAdminDisabled && !showSourceUnavailable ? (
        <ErrorAlert error={auditQuery.error} />
      ) : null}

      {!auditQuery.isLoading && data && data.items.length === 0 ? (
        <EmptyState title="No events" message="Try another source or adjust filters." />
      ) : null}

      {!auditQuery.isLoading && data && data.items.length > 0 && !isMobile ? (
        <Card className="overflow-x-auto p-0">
          <table className="min-w-full text-left text-xs">
            <thead className="border-b bg-slate-50 text-[11px] uppercase text-slate-500">
              <tr>
                <th className="p-2">createdAt</th>
                <th className="p-2">source</th>
                <th className="p-2">eventType</th>
                <th className="p-2">actorUserId</th>
                <th className="p-2">workspaceId</th>
                <th className="p-2">aggregate</th>
                <th className="p-2">requestId</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((row) => (
                <tr
                  key={`${row.source}-${row.id}`}
                  className="cursor-pointer border-b hover:bg-primary-50/40"
                  onClick={() => openDetail(row)}
                >
                  <td className="p-2 whitespace-nowrap">{row.createdAt}</td>
                  <td className="p-2">{row.source}</td>
                  <td className="p-2 font-medium">{row.eventType}</td>
                  <td className="p-2 font-mono text-[11px]">{row.actorUserId ?? '—'}</td>
                  <td className="p-2 font-mono text-[11px]">{row.workspaceId ?? '—'}</td>
                  <td className="p-2 text-[11px]">
                    {(row.aggregateType ?? '—') + ' / ' + (row.aggregateId ?? '—')}
                  </td>
                  <td className="p-2 font-mono text-[11px]">{row.requestId ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <PaginationControls
            page={parsedFromUrl.page}
            hasNext={data.hasNext}
            hasPrevious={data.hasPrevious}
            onNext={() => setPage(parsedFromUrl.page + 1)}
            onPrevious={() => setPage(Math.max(0, parsedFromUrl.page - 1))}
          />
        </Card>
      ) : null}
      {!auditQuery.isLoading && data && data.items.length > 0 && isMobile ? (
        <div className="space-y-2">
          {data.items.map((row) => (
            <Card key={`${row.source}-${row.id}`} className="space-y-2">
              <p className="text-xs text-slate-500">{row.createdAt}</p>
              <p className="text-sm font-semibold text-slate-900">{row.eventType}</p>
              <p className="text-xs text-slate-600">Source: {row.source}</p>
              <p className="truncate font-mono text-[11px] text-slate-500">actor: {row.actorUserId ?? '—'}</p>
              <div className="flex flex-wrap gap-2">
                <Button className="text-xs" type="button" onClick={() => openDetail(row)}>
                  Details
                </Button>
                <Button
                  type="button"
                  className="text-xs"
                  disabled={!row.requestId}
                  onClick={() => row.requestId && copyText(row.requestId)}
                >
                  Copy requestId
                </Button>
              </div>
            </Card>
          ))}
          <Card>
            <PaginationControls
              page={parsedFromUrl.page}
              hasNext={data.hasNext}
              hasPrevious={data.hasPrevious}
              onNext={() => setPage(parsedFromUrl.page + 1)}
              onPrevious={() => setPage(Math.max(0, parsedFromUrl.page - 1))}
            />
          </Card>
        </div>
      ) : null}

      {eventId && selectedDetail ? (
        <>
          <div className="fixed inset-0 z-30 bg-slate-900/40" onClick={closeDetail} aria-hidden />
          <aside className="fixed inset-y-0 right-0 z-40 w-full max-w-lg overflow-y-auto border-l bg-white shadow-xl">
            <div className="flex items-center justify-between border-b p-4">
              <div>
                <p className="text-xs uppercase text-slate-500">Event</p>
                <p className="font-mono text-xs">{selectedDetail.id}</p>
              </div>
              <Button className="text-xs" type="button" onClick={closeDetail}>
                Close
              </Button>
            </div>
            <div className="space-y-3 p-4 text-sm">
              <p>
                <span className="text-slate-500">Type:</span> {selectedDetail.eventType}
              </p>
              <p>
                <span className="text-slate-500">Source:</span> {selectedDetail.source}
              </p>
              <p>
                <span className="text-slate-500">Request:</span>{' '}
                <span className="break-all font-mono text-xs">{selectedDetail.requestId ?? '—'}</span>
              </p>
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  className="text-xs"
                  disabled={!selectedDetail.requestId}
                  onClick={() => selectedDetail.requestId && copyText(selectedDetail.requestId)}
                >
                  Copy requestId
                </Button>
                <Button type="button" className="text-xs" onClick={() => copyText(selectedDetail.id)}>
                  Copy eventId
                </Button>
              </div>

              <div>
                <p className="mb-2 text-xs font-semibold uppercase text-slate-500">
                  Metadata (masked for display)
                </p>
                <pre className="max-h-[50vh] overflow-auto rounded-md bg-slate-900 px-3 py-2 text-[11px] text-slate-100">
                  {JSON.stringify(maskSensitiveMetadata(selectedDetail.metadata), null, 2)}
                </pre>
              </div>
            </div>
          </aside>
        </>
      ) : null}

      {eventId && !selectedDetail && !auditQuery.isLoading ? (
        <Card>
          <p className="text-sm text-slate-600">
            Event detail is not available outside the loaded page window in non-mock modes until the
            Faz 43 admin audit proxy exposes per-event lookups.
          </p>
          <Button className="mt-2 text-xs" type="button" onClick={closeDetail}>
            Close detail
          </Button>
        </Card>
      ) : null}
    </div>
  )
}

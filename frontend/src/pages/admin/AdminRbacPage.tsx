import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'
import { LoadingState } from '../../shared/components/LoadingState'
import { ErrorAlert } from '../../shared/components/ErrorAlert'
import { PermissionDenied } from '../../shared/components/PermissionDenied'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_RBAC_CHANGE_REQUEST_CREATE,
  PERM_RBAC_READ,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import * as rbacApi from '../../features/admin/rbac/admin-rbac-api'
import {
  GITOPS_TARGET_ENVIRONMENTS,
  createChangeRequest,
  validateChangeRequest,
} from '../../features/admin/enterprise/change-requests-api'
import { isAdminRbacRoleRequestsUiEnabled, isAdminRbacUiEnabled } from '../../shared/config/admin-feature-flags'
import { isEnterpriseAdminWriteEnabled } from '../../shared/config/admin-feature-flags'
const ASSIGNABLE_ROLES = [
  'PLATFORM_ADMIN',
  'PLATFORM_AUDIT_VIEWER',
  'PLATFORM_AUDIT_EXPORTER',
  'PLATFORM_SECURITY_ADMIN',
  'PLATFORM_IDENTITY_ADMIN',
  'PLATFORM_CHANGE_REQUEST_AUTHOR',
  'PLATFORM_CHANGE_REQUEST_APPROVER',
  'PLATFORM_OBSERVABILITY_VIEWER',
] as const

export function AdminRbacPage() {
  const user = useAuthStore((s) => s.user)
  const canRead = hasPlatformPermission(user, PERM_RBAC_READ)
  const uiOn = isAdminRbacUiEnabled()
  const roleReqOn = isAdminRbacRoleRequestsUiEnabled() && isEnterpriseAdminWriteEnabled()
  const canRoleRequest = hasPlatformPermission(user, PERM_RBAC_CHANGE_REQUEST_CREATE)

  const [q, setQ] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [permFilter, setPermFilter] = useState('')
  const [page, setPage] = useState(0)
  const [detailId, setDetailId] = useState<string | null>(null)

  const listQ = useQuery({
    queryKey: ['admin-rbac-users', q, roleFilter, permFilter, page],
    queryFn: () =>
      rbacApi.listAdminRbacUsers({
        q: q || undefined,
        role: roleFilter || undefined,
        permission: permFilter || undefined,
        page,
        size: 25,
      }),
    enabled: uiOn && canRead,
  })

  const detailQ = useQuery({
    queryKey: ['admin-rbac-user', detailId],
    queryFn: () => rbacApi.getAdminRbacUser(detailId!),
    enabled: !!detailId && uiOn && canRead,
  })

  const selected =
    detailQ.data?.user.userId === detailId ? detailQ.data.user : undefined

  if (!uiOn) {
    return (
      <div className="space-y-4">
        <PageHeader title="Admin RBAC" subtitle="Directory is disabled by configuration." />
        <Card>
          <p className="text-sm text-slate-600">
            Set <code className="text-xs">ADMIN_RBAC_UI_ENABLED</code> (via{' '}
            <code className="text-xs">FRONTEND_ADMIN_RBAC_UI_ENABLED</code> in deployment env) to enable this view.
          </p>
        </Card>
      </div>
    )
  }

  if (!canRead) {
    return (
      <PermissionDenied
        title="RBAC directory restricted"
        message="Your account needs the admin:rbac:read permission (or platform admin)."
      />
    )
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="Admin RBAC"
        subtitle="Effective platform roles and sources (SSO / SCIM / legacy). No runtime role changes."
      />
      <div className="flex flex-wrap gap-2 text-xs text-slate-500">
        <Link to="/app/admin" className="text-primary-600 hover:underline">
          Admin home
        </Link>
        <Link to="/app/admin/enterprise/change-requests" className="text-primary-600 hover:underline">
          Change requests
        </Link>
      </div>

      <Card className="flex flex-wrap gap-3">
        <label className="flex flex-col gap-1 text-xs">
          <span className="font-medium text-slate-600">Search</span>
          <input
            className="rounded border border-slate-200 px-2 py-1 text-sm"
            value={q}
            onChange={(e) => {
              setQ(e.target.value)
              setPage(0)
            }}
            placeholder="Email or name prefix"
          />
        </label>
        <label className="flex flex-col gap-1 text-xs">
          <span className="font-medium text-slate-600">Role</span>
          <select
            className="rounded border border-slate-200 px-2 py-1 text-sm"
            value={roleFilter}
            onChange={(e) => {
              setRoleFilter(e.target.value)
              setPage(0)
            }}
          >
            <option value="">Any</option>
            {ASSIGNABLE_ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-xs">
          <span className="font-medium text-slate-600">Permission</span>
          <input
            className="rounded border border-slate-200 px-2 py-1 text-sm"
            value={permFilter}
            onChange={(e) => {
              setPermFilter(e.target.value)
              setPage(0)
            }}
            placeholder="e.g. admin:audit:read"
          />
        </label>
      </Card>

      {listQ.isLoading ? <LoadingState /> : null}
      {listQ.error ? <ErrorAlert error={listQ.error} /> : null}

      {listQ.data ? (
        <Card className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead>
              <tr className="border-b text-xs text-slate-500">
                <th className="py-2 pr-2">Email</th>
                <th className="py-2 pr-2">Status</th>
                <th className="py-2 pr-2">Roles</th>
                <th className="py-2 pr-2">Perms</th>
                <th className="py-2 pr-2">Sources</th>
                <th className="py-2 pr-2">Last login</th>
                <th className="py-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              {listQ.data.items.map((row) => (
                <tr key={row.userId} className="border-b border-slate-100">
                  <td className="py-2 pr-2 font-medium text-slate-800">{row.email}</td>
                  <td className="py-2 pr-2 text-xs">{row.status}</td>
                  <td className="py-2 pr-2 text-xs">{row.platformRoles.join(', ') || '—'}</td>
                  <td className="py-2 pr-2 text-xs">{row.platformPermissions.length}</td>
                  <td className="py-2 pr-2 text-xs">
                    {row.sources.map((s) => `${s.type}:${s.sourceName}`).join('; ') || '—'}
                  </td>
                  <td className="py-2 pr-2 text-xs text-slate-500">
                    {row.lastLoginAt ? new Date(row.lastLoginAt).toLocaleString() : '—'}
                  </td>
                  <td className="py-2">
                    <button
                      type="button"
                      className="text-primary-700 text-xs underline"
                      onClick={() => setDetailId(row.userId)}
                    >
                      View
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="mt-3 flex items-center justify-between text-xs text-slate-500">
            <span>
              Page {listQ.data.page + 1} · {listQ.data.totalElements} total (server)
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                className="rounded border px-2 py-1"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Prev
              </button>
              <button
                type="button"
                className="rounded border px-2 py-1"
                disabled={(page + 1) * 25 >= listQ.data.totalElements}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          </div>
        </Card>
      ) : null}

      {detailId ? (
        <DetailDrawer
          userId={detailId}
          onClose={() => setDetailId(null)}
          detail={selected}
          loading={detailQ.isLoading}
          error={detailQ.error}
          roleRequestsEnabled={roleReqOn && canRoleRequest}
        />
      ) : null}
    </div>
  )
}

function DetailDrawer({
  userId,
  onClose,
  detail,
  loading,
  error,
  roleRequestsEnabled,
}: {
  userId: string
  onClose: () => void
  detail: rbacApi.AdminRbacUserRow | undefined
  loading: boolean
  error: unknown
  roleRequestsEnabled: boolean
}) {
  const [modal, setModal] = useState<'grant' | 'revoke' | null>(null)
  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-black/20">
      <div className="h-full w-full max-w-lg overflow-y-auto bg-white shadow-xl">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <h2 className="text-sm font-semibold text-slate-900">User RBAC</h2>
          <button type="button" className="text-sm text-slate-500 hover:text-slate-800" onClick={onClose}>
            Close
          </button>
        </div>
        <div className="space-y-3 p-4 text-sm">
          {loading ? <LoadingState /> : null}
          {error ? <ErrorAlert error={error} /> : null}
          {detail ? (
            <>
              <p className="font-medium text-slate-900">{detail.email}</p>
              <p className="text-xs text-slate-500">Status: {detail.status}</p>
              {detail.warnings.length ? (
                <ul className="list-inside list-disc text-xs text-amber-800">
                  {detail.warnings.map((w) => (
                    <li key={w}>{w}</li>
                  ))}
                </ul>
              ) : null}
              <div>
                <p className="text-xs font-semibold uppercase text-slate-500">Roles</p>
                <p className="text-xs text-slate-700">{detail.platformRoles.join(', ') || '—'}</p>
              </div>
              <div>
                <p className="text-xs font-semibold uppercase text-slate-500">Permissions</p>
                <ul className="max-h-40 overflow-auto text-xs text-slate-700">
                  {detail.platformPermissions.map((p) => (
                    <li key={p}>{p}</li>
                  ))}
                </ul>
              </div>
              <div>
                <p className="text-xs font-semibold uppercase text-slate-500">Sources</p>
                <ul className="text-xs text-slate-700">
                  {detail.sources.map((s) => (
                    <li key={`${s.type}-${s.sourceName}`}>
                      {s.type}: {s.sourceName} → {s.roles.join(', ')}
                    </li>
                  ))}
                </ul>
              </div>
              {roleRequestsEnabled ? (
                <div className="flex flex-wrap gap-2 border-t pt-3">
                  <button
                    type="button"
                    className="rounded bg-primary-600 px-3 py-1.5 text-xs text-white"
                    onClick={() => setModal('grant')}
                  >
                    Request role grant
                  </button>
                  <button
                    type="button"
                    className="rounded border border-slate-200 px-3 py-1.5 text-xs"
                    onClick={() => setModal('revoke')}
                  >
                    Request role revoke
                  </button>
                </div>
              ) : null}
            </>
          ) : null}
        </div>
      </div>
      {modal ? (
        <RoleRequestModal
          userId={userId}
          mode={modal}
          onClose={() => setModal(null)}
        />
      ) : null}
    </div>
  )
}

function RoleRequestModal({
  userId,
  mode,
  onClose,
}: {
  userId: string
  mode: 'grant' | 'revoke'
  onClose: () => void
}) {
  const [role, setRole] = useState<string>(ASSIGNABLE_ROLES[0])
  const [reason, setReason] = useState('')
  const [confirm, setConfirm] = useState('')
  const [targetEnv, setTargetEnv] = useState<string>(GITOPS_TARGET_ENVIRONMENTS[1] ?? 'staging')
  const [msg, setMsg] = useState<string | null>(null)

  const opType =
    mode === 'grant' ? 'ADMIN_RBAC_ROLE_GRANT_REQUEST' : 'ADMIN_RBAC_ROLE_REVOKE_REQUEST'

  const validateM = useMutation({
    mutationFn: () =>
      validateChangeRequest({
        operationType: opType,
        requestedValue: `${mode === 'grant' ? 'GRANT' : 'REVOKE'}:${role}:${userId}`,
        targetEnvironment: targetEnv,
        structuredPayload: {
          userId,
          role,
          action: mode === 'grant' ? 'GRANT' : 'REVOKE',
          reason: reason.trim(),
        },
      }),
  })

  const createM = useMutation({
    mutationFn: () =>
      createChangeRequest({
        operationType: opType,
        requestedValue: `${mode === 'grant' ? 'GRANT' : 'REVOKE'}:${role}:${userId}`,
        targetEnvironment: targetEnv,
        confirmation: role === 'PLATFORM_ADMIN' ? confirm.trim() : undefined,
        structuredPayload: {
          userId,
          role,
          action: mode === 'grant' ? 'GRANT' : 'REVOKE',
          reason: reason.trim(),
        },
      }),
  })

  const needsConfirm = role === 'PLATFORM_ADMIN'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <Card className="max-w-md space-y-3">
        <h3 className="text-sm font-semibold text-slate-900">
          {mode === 'grant' ? 'Request role grant' : 'Request role revoke'}
        </h3>
        <label className="flex flex-col gap-1 text-xs">
          Role
          <select
            className="rounded border px-2 py-1 text-sm"
            value={role}
            onChange={(e) => setRole(e.target.value)}
          >
            {ASSIGNABLE_ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-xs">
          Target environment
          <select
            className="rounded border px-2 py-1 text-sm"
            value={targetEnv}
            onChange={(e) => setTargetEnv(e.target.value)}
          >
            {GITOPS_TARGET_ENVIRONMENTS.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-xs">
          Reason (required)
          <textarea
            className="rounded border px-2 py-1 text-sm"
            rows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        {needsConfirm ? (
          <label className="flex flex-col gap-1 text-xs text-amber-900">
            Type CONFIRM for PLATFORM_ADMIN
            <input
              className="rounded border px-2 py-1 text-sm"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
            />
          </label>
        ) : null}
        {msg ? (
          <div className="space-y-1 text-xs text-emerald-800">
            <p>{msg}</p>
            {createM.isSuccess && createM.data?.id ? (
              <Link
                className="inline-block font-medium text-primary-700 underline"
                to={`/app/admin/enterprise/change-requests?cr=${encodeURIComponent(createM.data.id)}`}
              >
                View change request
              </Link>
            ) : null}
          </div>
        ) : null}
        {validateM.error ? <ErrorAlert error={validateM.error} /> : null}
        {createM.error ? <ErrorAlert error={createM.error} /> : null}
        <div className="flex justify-end gap-2">
          <button type="button" className="rounded border px-3 py-1.5 text-xs" onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="rounded border px-3 py-1.5 text-xs"
            disabled={validateM.isPending}
            onClick={() => validateM.mutate()}
          >
            Validate
          </button>
          <button
            type="button"
            className="rounded bg-primary-600 px-3 py-1.5 text-xs text-white disabled:opacity-50"
            disabled={
              createM.isPending ||
              !reason.trim() ||
              (needsConfirm && confirm.trim() !== 'CONFIRM')
            }
            onClick={() =>
              createM.mutate(undefined, {
                onSuccess: (res) => {
                  setMsg(`Created change request ${res.id}.`)
                },
              })
            }
          >
            Create request
          </button>
        </div>
        <p className="text-[11px] text-slate-500">
          Applies only after approval and GitOps/runbook — no automatic role mutation.
        </p>
        <Link className="text-xs text-primary-700 underline" to="/app/admin/enterprise/change-requests">
          Open change requests
        </Link>
      </Card>
    </div>
  )
}

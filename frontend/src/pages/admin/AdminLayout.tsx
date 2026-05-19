import { NavLink, Outlet } from 'react-router-dom'
import { useAuthStore } from '../../features/auth/auth-store'
import {
  PERM_AUDIT_READ,
  PERM_CHANGE_REQUEST_LIST,
  PERM_ENTERPRISE_STATUS_READ,
  PERM_NOTIFICATIONS_ANALYTICS_READ,
  PERM_NOTIFICATIONS_DEAD_LETTER_READ,
  PERM_NOTIFICATIONS_RETENTION_READ,
  PERM_NOTIFICATIONS_LEGAL_HOLD_READ,
  PERM_RETENTION_READ,
  PERM_RBAC_READ,
  PERM_BREAK_GLASS_READ,
  PERM_BREAK_GLASS_ROTATION_READ,
  hasPlatformPermission,
} from '../../features/admin/access/admin-permissions'
import {
  getAuditApiMode,
  isAdminUiDevOpen,
  isEnterpriseAdminWriteEnabled,
  isNotificationAnalyticsUiEnabled,
  isNotificationDeadLetterUiEnabled,
  isNotificationRetentionUiEnabled,
  isNotificationLegalHoldUiEnabled,
  isPlatformRetentionGovernanceUiEnabled,
  isAdminRbacUiEnabled,
  isBreakGlassReviewUiEnabled,
  isBreakGlassRotationUiEnabled,
  isScimCompatibilityDiagnosticsUiEnabled,
} from '../../shared/config/admin-feature-flags'

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `block rounded px-2 py-1.5 text-sm ${isActive ? 'bg-primary-100 font-medium text-primary-800' : 'text-slate-600 hover:bg-slate-100'}`

export function AdminLayout() {
  const user = useAuthStore((s) => s.user)
  const devNavOpen = isAdminUiDevOpen() && getAuditApiMode() === 'mock'
  const showAudit = devNavOpen || hasPlatformPermission(user, PERM_AUDIT_READ)
  const showEnterprise = devNavOpen || hasPlatformPermission(user, PERM_ENTERPRISE_STATUS_READ)
  const showIdentity = showEnterprise
  const showScim = showEnterprise && isScimCompatibilityDiagnosticsUiEnabled()
  const showRoleMapping = isAdminRbacUiEnabled() && (devNavOpen || hasPlatformPermission(user, PERM_RBAC_READ))
  const showChangeRequests =
    isEnterpriseAdminWriteEnabled() &&
    (devNavOpen || hasPlatformPermission(user, PERM_CHANGE_REQUEST_LIST))
  const showNotificationAnalytics =
    isNotificationAnalyticsUiEnabled() &&
    (devNavOpen || hasPlatformPermission(user, PERM_NOTIFICATIONS_ANALYTICS_READ))
  const showNotificationDeadLetter =
    isNotificationDeadLetterUiEnabled() &&
    (devNavOpen || hasPlatformPermission(user, PERM_NOTIFICATIONS_DEAD_LETTER_READ))
  const showNotificationRetention =
    isNotificationRetentionUiEnabled() &&
    (devNavOpen || hasPlatformPermission(user, PERM_NOTIFICATIONS_RETENTION_READ))
  const showNotificationLegalHolds =
    isNotificationLegalHoldUiEnabled() &&
    (devNavOpen || hasPlatformPermission(user, PERM_NOTIFICATIONS_LEGAL_HOLD_READ))
  const showPlatformRetention =
    isPlatformRetentionGovernanceUiEnabled() &&
    (devNavOpen || hasPlatformPermission(user, PERM_RETENTION_READ))
  const showAdminRbac = showRoleMapping
  const showBreakGlassReview =
    isBreakGlassReviewUiEnabled() &&
    (devNavOpen || hasPlatformPermission(user, PERM_BREAK_GLASS_READ))
  const showBreakGlassRotation =
    isBreakGlassRotationUiEnabled() &&
    (devNavOpen || hasPlatformPermission(user, PERM_BREAK_GLASS_ROTATION_READ))

  return (
    <div className="flex flex-col gap-4 lg:flex-row lg:gap-8">
      <nav className="shrink-0 lg:w-56">
        <p className="mb-2 text-xs font-semibold uppercase text-slate-500">Admin</p>
        <ul className="space-y-1">
          <li>
            <NavLink to="/app/admin/overview" className={linkClass}>
              Overview
            </NavLink>
          </li>
          {showEnterprise ? (
            <li>
              <NavLink to="/app/admin/setup" className={linkClass}>
                Setup checklist
              </NavLink>
            </li>
          ) : null}
          <li>
            <NavLink to="/app/admin/search" className={linkClass}>
              Search & diagnostics
            </NavLink>
          </li>
          {showChangeRequests ? (
            <li>
              <NavLink to="/app/admin/change-requests" className={linkClass}>
                Change requests
              </NavLink>
            </li>
          ) : null}
          {showAudit ? (
            <li>
              <NavLink to="/app/admin/audit" className={linkClass}>
                Audit events
              </NavLink>
            </li>
          ) : null}
          {showIdentity ? (
            <>
              <li className="pt-2 text-[10px] font-semibold uppercase text-slate-400">Identity</li>
              <li>
                <NavLink to="/app/admin/identity" end className={linkClass}>
                  Identity overview
                </NavLink>
              </li>
              <li className="pl-3">
                <NavLink to="/app/admin/identity/sso" className={linkClass}>
                  SSO diagnostics
                </NavLink>
              </li>
              {showScim ? (
                <li className="pl-3">
                  <NavLink to="/app/admin/identity/scim" className={linkClass}>
                    SCIM provisioning
                  </NavLink>
                </li>
              ) : null}
              {showRoleMapping ? (
                <li className="pl-3">
                  <NavLink to="/app/admin/identity/role-mapping" className={linkClass}>
                    Role mapping
                  </NavLink>
                </li>
              ) : null}
            </>
          ) : null}
          {showBreakGlassReview ? (
            <li>
              <NavLink to="/app/admin/security/break-glass" className={linkClass}>
                Break-glass ops
              </NavLink>
            </li>
          ) : null}
          {showBreakGlassRotation ? (
            <li className="pl-3">
              <NavLink to="/app/admin/security/break-glass/rotation" className={linkClass}>
                Break-glass rotation
              </NavLink>
            </li>
          ) : null}
          <li className="pt-2 text-[10px] font-semibold uppercase text-slate-400">Legacy</li>
          {showEnterprise ? (
            <li>
              <NavLink to="/app/admin/enterprise" end className={linkClass}>
                Enterprise console
              </NavLink>
            </li>
          ) : null}
          {showAdminRbac ? (
            <li>
              <NavLink to="/app/admin/rbac" className={linkClass}>
                Admin RBAC
              </NavLink>
            </li>
          ) : null}
          {showChangeRequests ? (
            <li className="pl-3">
              <NavLink to="/app/admin/enterprise/change-requests" className={linkClass}>
                Change requests
              </NavLink>
            </li>
          ) : null}
          {showNotificationAnalytics ? (
            <li>
              <NavLink to="/app/admin/notifications/analytics" className={linkClass}>
                Notification analytics
              </NavLink>
            </li>
          ) : null}
          {showNotificationDeadLetter ? (
            <li>
              <NavLink to="/app/admin/notifications/dead-letter" className={linkClass}>
                Notification dead-letter
              </NavLink>
            </li>
          ) : null}
          {showNotificationRetention ? (
            <li>
              <NavLink to="/app/admin/notifications/retention" className={linkClass}>
                Notification retention
              </NavLink>
            </li>
          ) : null}
          {showNotificationLegalHolds ? (
            <li>
              <NavLink to="/app/admin/notifications/legal-holds" className={linkClass}>
                Legal holds
              </NavLink>
            </li>
          ) : null}
          {(showPlatformRetention || showNotificationRetention) ? (
            <>
              <li className="pt-2 text-[10px] font-semibold uppercase text-slate-400">Retention</li>
              <li>
                <NavLink to="/app/admin/retention" end className={linkClass}>
                  Retention hub
                </NavLink>
              </li>
              {showPlatformRetention ? (
                <li className="pl-3">
                  <NavLink to="/app/admin/retention/platform" className={linkClass}>
                    Platform governance
                  </NavLink>
                </li>
              ) : null}
              {showPlatformRetention || showNotificationLegalHolds ? (
                <li className="pl-3">
                  <NavLink to="/app/admin/retention/legal-holds" className={linkClass}>
                    Legal holds (platform)
                  </NavLink>
                </li>
              ) : null}
            </>
          ) : null}
        </ul>
      </nav>
      <div className="min-w-0 flex-1">
        <Outlet />
      </div>
    </div>
  )
}

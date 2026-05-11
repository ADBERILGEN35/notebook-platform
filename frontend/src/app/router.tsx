import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom'
import type { ReactNode } from 'react'
import { LoginPage } from '../pages/LoginPage'
import { SignupPage } from '../pages/SignupPage'
import { AppShellPage } from '../pages/AppShellPage'
import { WorkspacePage } from '../pages/WorkspacePage'
import { NotebookPage } from '../pages/NotebookPage'
import { NotePage } from '../pages/NotePage'
import { SearchPage } from '../pages/SearchPage'
import { SettingsPage } from '../pages/SettingsPage'
import { NotificationsPage } from '../pages/NotificationsPage'
import { AdminHomePage } from '../pages/admin/AdminHomePage'
import { AdminAuditPage } from '../pages/admin/AdminAuditPage'
import { AdminLayout } from '../pages/admin/AdminLayout'
import { AdminEnterpriseLayout } from '../pages/admin/AdminEnterpriseLayout'
import {
  AdminEnterpriseOverviewPage,
  AdminEnterpriseSecurityPage,
  AdminEnterpriseIntegrationsPage,
} from '../pages/admin/AdminEnterprisePages'
import { AdminEnterpriseChangeRequestsPage } from '../pages/admin/AdminEnterpriseChangeRequestsPage'
import { AdminNotificationAnalyticsPage } from '../pages/admin/AdminNotificationAnalyticsPage'
import { AdminNotificationDeadLetterPage } from '../pages/admin/AdminNotificationDeadLetterPage'
import { AdminNotificationRetentionPage } from '../pages/admin/AdminNotificationRetentionPage'
import { AdminNotificationLegalHoldsPage } from '../pages/admin/AdminNotificationLegalHoldsPage'
import { AdminRbacPage } from '../pages/admin/AdminRbacPage'
import { AdminBreakGlassPage } from '../pages/admin/AdminBreakGlassPage'
import { useAuthStore } from '../features/auth/auth-store'
import { isCookieMode } from '../shared/config/auth-transport'
import { isAdminUiEnabled } from '../shared/config/admin-feature-flags'
import { canShowAdminNavigation } from '../features/admin/access/admin-access'
import { PermissionDenied } from '../shared/components/PermissionDenied'

function Protected({ children }: { children: ReactNode }) {
  const token = useAuthStore((state) => state.accessToken)
  const user = useAuthStore((state) => state.user)
  if (isCookieMode()) {
    if (!user) return <Navigate to="/login" replace />
    return <>{children}</>
  }
  if (!token) return <Navigate to="/login" replace />
  return <>{children}</>
}

function AdminGate() {
  const user = useAuthStore((state) => state.user)
  if (!isAdminUiEnabled()) {
    return <Navigate to="/app" replace />
  }
  if (!canShowAdminNavigation(user)) {
    return (
      <PermissionDenied
        title="Admin area restricted"
        message="Admin UI requires feature flags and a trusted role. Local development may set ADMIN_UI_DEV_OPEN; production requires platform admin authorization (planned Faz 43)."
      />
    )
  }
  return <Outlet />
}

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/app" replace /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  {
    path: '/app',
    element: (
      <Protected>
        <AppShellPage />
      </Protected>
    ),
    children: [
      { index: true, element: <WorkspacePage /> },
      { path: 'workspaces/:workspaceId', element: <WorkspacePage /> },
      { path: 'notebooks/:notebookId', element: <NotebookPage /> },
      { path: 'notes/:noteId', element: <NotePage /> },
      { path: 'search', element: <SearchPage /> },
      { path: 'notifications', element: <NotificationsPage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: 'settings/security', element: <SettingsPage /> },
      { path: 'settings/notifications', element: <SettingsPage /> },
      {
        path: 'admin',
        element: <AdminGate />,
        children: [
          {
            element: <AdminLayout />,
            children: [
              { index: true, element: <AdminHomePage /> },
              { path: 'audit', element: <AdminAuditPage /> },
              { path: 'audit/:eventId', element: <AdminAuditPage /> },
              { path: 'notifications/analytics', element: <AdminNotificationAnalyticsPage /> },
              { path: 'notifications/dead-letter', element: <AdminNotificationDeadLetterPage /> },
              { path: 'notifications/retention', element: <AdminNotificationRetentionPage /> },
              { path: 'notifications/legal-holds', element: <AdminNotificationLegalHoldsPage /> },
              { path: 'rbac', element: <AdminRbacPage /> },
              { path: 'security/break-glass', element: <AdminBreakGlassPage /> },
              {
                path: 'enterprise',
                element: <AdminEnterpriseLayout />,
                children: [
                  { index: true, element: <AdminEnterpriseOverviewPage /> },
                  { path: 'security', element: <AdminEnterpriseSecurityPage /> },
                  { path: 'integrations', element: <AdminEnterpriseIntegrationsPage /> },
                  { path: 'change-requests', element: <AdminEnterpriseChangeRequestsPage /> },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
])


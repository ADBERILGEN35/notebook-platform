import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom'
import type { ReactNode } from 'react'
import { LoginPage } from '../pages/LoginPage'
import { RegisterPage } from '../pages/RegisterPage'
import { SignupPage } from '../pages/SignupPage'
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage'
import { MfaAuthenticationPage } from '../pages/MfaAuthenticationPage'
import { SsoCallbackPage } from '../pages/SsoCallbackPage'
import { AppShellPage } from '../pages/AppShellPage'
import { WorkspaceHubPage } from '../pages/WorkspaceHubPage'
import { NotebookPage } from '../pages/NotebookPage'
import { NotePage } from '../pages/NotePage'
import { NoteEditorPage } from '../pages/NoteEditorPage'
import { NoteEditorHistoryPage } from '../pages/NoteEditorHistoryPage'
import { WorkspaceMembersPage } from '../pages/WorkspaceMembersPage'
import { WorkspaceSettingsPage } from '../pages/WorkspaceSettingsPage'
import { SearchResultsPage } from '../pages/SearchResultsPage'
import { SearchDiscoveryPage } from '../pages/SearchDiscoveryPage'
import { SettingsLayout } from '../features/settings/SettingsLayout'
import { UserSettingsPage } from '../pages/UserSettingsPage'
import { AccountSecurityPage } from '../pages/AccountSecurityPage'
import { NotificationPreferencesPage } from '../pages/NotificationPreferencesPage'
import { OfflineSyncDiagnosticsPage } from '../pages/OfflineSyncDiagnosticsPage'
import { NotificationCenterPage } from '../pages/NotificationCenterPage'
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
import { AdminPlatformRetentionPage } from '../pages/admin/AdminPlatformRetentionPage'
import { AdminRbacPage } from '../pages/admin/AdminRbacPage'
import { AdminBreakGlassPage } from '../pages/admin/AdminBreakGlassPage'
import { AdminBreakGlassRotationPage } from '../pages/admin/AdminBreakGlassRotationPage'
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
  { path: '/register', element: <RegisterPage /> },
  { path: '/signup', element: <SignupPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/mfa', element: <MfaAuthenticationPage /> },
  { path: '/sso/callback', element: <SsoCallbackPage /> },
  {
    path: '/app',
    element: (
      <Protected>
        <AppShellPage />
      </Protected>
    ),
    children: [
      { index: true, element: <WorkspaceHubPage /> },
      { path: 'workspaces', element: <WorkspaceHubPage /> },
      { path: 'workspaces/:workspaceId', element: <WorkspaceHubPage /> },
      { path: 'workspaces/:workspaceId/members', element: <WorkspaceMembersPage /> },
      { path: 'workspaces/:workspaceId/settings', element: <WorkspaceSettingsPage /> },
      { path: 'workspaces/:workspaceId/notes/:noteId', element: <NoteEditorPage /> },
      { path: 'workspaces/:workspaceId/notes/:noteId/history', element: <NoteEditorHistoryPage /> },
      { path: 'notebooks/:notebookId', element: <NotebookPage /> },
      { path: 'notes/:noteId', element: <NotePage /> },
      { path: 'search', element: <SearchResultsPage /> },
      { path: 'search/discover', element: <SearchDiscoveryPage /> },
      { path: 'notifications', element: <NotificationCenterPage /> },
      {
        path: 'settings',
        element: <SettingsLayout />,
        children: [
          { index: true, element: <UserSettingsPage /> },
          { path: 'security', element: <AccountSecurityPage /> },
          { path: 'notifications', element: <NotificationPreferencesPage /> },
          { path: 'sync', element: <OfflineSyncDiagnosticsPage /> },
        ],
      },
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
              { path: 'retention/platform', element: <AdminPlatformRetentionPage /> },
              { path: 'rbac', element: <AdminRbacPage /> },
              { path: 'security/break-glass', element: <AdminBreakGlassPage /> },
              { path: 'security/break-glass/rotation', element: <AdminBreakGlassRotationPage /> },
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


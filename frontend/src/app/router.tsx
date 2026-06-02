import { lazy } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RouteError } from './ErrorBoundary'
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
import { AdminLayout } from '../pages/admin/AdminLayout'
import { AdminEnterpriseLayout } from '../pages/admin/AdminEnterpriseLayout'
import { AdminGate, Protected } from './route-guards'

// Admin leaf pages are lazy-loaded so the ~40 admin components are not bundled
// into the initial chunk for non-admin users. The Suspense boundary lives in
// AdminLayout (around its <Outlet/>), covering all nested admin routes.
const AdminHomePage = lazy(() =>
  import('../pages/admin/AdminHomePage').then((m) => ({ default: m.AdminHomePage })),
)
const AdminOverviewPage = lazy(() =>
  import('../pages/admin/AdminOverviewPage').then((m) => ({ default: m.AdminOverviewPage })),
)
const AdminSetupChecklistPage = lazy(() =>
  import('../pages/admin/AdminSetupChecklistPage').then((m) => ({
    default: m.AdminSetupChecklistPage,
  })),
)
const AdminSearchDiagnosticsPage = lazy(() =>
  import('../pages/admin/AdminSearchDiagnosticsPage').then((m) => ({
    default: m.AdminSearchDiagnosticsPage,
  })),
)
const AdminIdentityOverviewPage = lazy(() =>
  import('../pages/admin/AdminIdentityOverviewPage').then((m) => ({
    default: m.AdminIdentityOverviewPage,
  })),
)
const AdminSsoDiagnosticsPage = lazy(() =>
  import('../pages/admin/AdminSsoDiagnosticsPage').then((m) => ({
    default: m.AdminSsoDiagnosticsPage,
  })),
)
const AdminScimProvisioningPage = lazy(() =>
  import('../pages/admin/AdminScimProvisioningPage').then((m) => ({
    default: m.AdminScimProvisioningPage,
  })),
)
const AdminRoleMappingDiagnosticsPage = lazy(() =>
  import('../pages/admin/AdminRoleMappingDiagnosticsPage').then((m) => ({
    default: m.AdminRoleMappingDiagnosticsPage,
  })),
)
const AdminBreakGlassOpsPage = lazy(() =>
  import('../pages/admin/AdminBreakGlassOpsPage').then((m) => ({
    default: m.AdminBreakGlassOpsPage,
  })),
)
const AdminAuditPage = lazy(() =>
  import('../pages/admin/AdminAuditPage').then((m) => ({ default: m.AdminAuditPage })),
)
const AdminEnterpriseOverviewPage = lazy(() =>
  import('../pages/admin/AdminEnterprisePages').then((m) => ({
    default: m.AdminEnterpriseOverviewPage,
  })),
)
const AdminEnterpriseSecurityPage = lazy(() =>
  import('../pages/admin/AdminEnterprisePages').then((m) => ({
    default: m.AdminEnterpriseSecurityPage,
  })),
)
const AdminEnterpriseIntegrationsPage = lazy(() =>
  import('../pages/admin/AdminEnterprisePages').then((m) => ({
    default: m.AdminEnterpriseIntegrationsPage,
  })),
)
const AdminEnterpriseChangeRequestsPage = lazy(() =>
  import('../pages/admin/AdminEnterpriseChangeRequestsPage').then((m) => ({
    default: m.AdminEnterpriseChangeRequestsPage,
  })),
)
const AdminNotificationAnalyticsPage = lazy(() =>
  import('../pages/admin/AdminNotificationAnalyticsPage').then((m) => ({
    default: m.AdminNotificationAnalyticsPage,
  })),
)
const AdminNotificationDeadLetterPage = lazy(() =>
  import('../pages/admin/AdminNotificationDeadLetterPage').then((m) => ({
    default: m.AdminNotificationDeadLetterPage,
  })),
)
const AdminNotificationRetentionPage = lazy(() =>
  import('../pages/admin/AdminNotificationRetentionPage').then((m) => ({
    default: m.AdminNotificationRetentionPage,
  })),
)
const AdminNotificationLegalHoldsPage = lazy(() =>
  import('../pages/admin/AdminNotificationLegalHoldsPage').then((m) => ({
    default: m.AdminNotificationLegalHoldsPage,
  })),
)
const AdminPlatformRetentionPage = lazy(() =>
  import('../pages/admin/AdminPlatformRetentionPage').then((m) => ({
    default: m.AdminPlatformRetentionPage,
  })),
)
const AdminNotificationDeadLetterDetailPage = lazy(() =>
  import('../pages/admin/AdminNotificationDeadLetterDetailPage').then((m) => ({
    default: m.AdminNotificationDeadLetterDetailPage,
  })),
)
const AdminNotificationDeadLetterRequeuePage = lazy(() =>
  import('../pages/admin/AdminNotificationDeadLetterRequeuePage').then((m) => ({
    default: m.AdminNotificationDeadLetterRequeuePage,
  })),
)
const AdminRetentionHubPage = lazy(() =>
  import('../pages/admin/AdminRetentionHubPage').then((m) => ({
    default: m.AdminRetentionHubPage,
  })),
)
const AdminPlatformLegalHoldsPage = lazy(() =>
  import('../pages/admin/AdminPlatformLegalHoldsPage').then((m) => ({
    default: m.AdminPlatformLegalHoldsPage,
  })),
)
const AdminPurgeResultPage = lazy(() =>
  import('../pages/admin/AdminPurgeResultPage').then((m) => ({
    default: m.AdminPurgeResultPage,
  })),
)
const AdminRbacPage = lazy(() =>
  import('../pages/admin/AdminRbacPage').then((m) => ({ default: m.AdminRbacPage })),
)
const AdminBreakGlassRotationPage = lazy(() =>
  import('../pages/admin/AdminBreakGlassRotationPage').then((m) => ({
    default: m.AdminBreakGlassRotationPage,
  })),
)
const AdminChangeRequestsPage = lazy(() =>
  import('../pages/admin/AdminChangeRequestsPage').then((m) => ({
    default: m.AdminChangeRequestsPage,
  })),
)
const AdminChangeRequestDetailPage = lazy(() =>
  import('../pages/admin/AdminChangeRequestDetailPage').then((m) => ({
    default: m.AdminChangeRequestDetailPage,
  })),
)
const AdminChangeRequestGitOpsPage = lazy(() =>
  import('../pages/admin/AdminChangeRequestGitOpsPage').then((m) => ({
    default: m.AdminChangeRequestGitOpsPage,
  })),
)
const AdminChangeRequestDryRunPage = lazy(() =>
  import('../pages/admin/AdminChangeRequestDryRunPage').then((m) => ({
    default: m.AdminChangeRequestDryRunPage,
  })),
)
const AdminChangeRequestDiffPage = lazy(() =>
  import('../pages/admin/AdminChangeRequestDiffPage').then((m) => ({
    default: m.AdminChangeRequestDiffPage,
  })),
)

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
    errorElement: <RouteError />,
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
              { path: 'overview', element: <AdminOverviewPage /> },
              { path: 'setup', element: <AdminSetupChecklistPage /> },
              { path: 'search', element: <AdminSearchDiagnosticsPage /> },
              { path: 'audit', element: <AdminAuditPage /> },
              { path: 'audit/:eventId', element: <AdminAuditPage /> },
              { path: 'identity', element: <AdminIdentityOverviewPage /> },
              { path: 'identity/sso', element: <AdminSsoDiagnosticsPage /> },
              { path: 'identity/scim', element: <AdminScimProvisioningPage /> },
              { path: 'identity/role-mapping', element: <AdminRoleMappingDiagnosticsPage /> },
              { path: 'notifications/analytics', element: <AdminNotificationAnalyticsPage /> },
              { path: 'notifications/dead-letter', element: <AdminNotificationDeadLetterPage /> },
              { path: 'notifications/dead-letter/:eventId', element: <AdminNotificationDeadLetterDetailPage /> },
              {
                path: 'notifications/dead-letter/:eventId/requeue',
                element: <AdminNotificationDeadLetterRequeuePage />,
              },
              { path: 'notifications/retention', element: <AdminNotificationRetentionPage /> },
              { path: 'notifications/legal-holds', element: <AdminNotificationLegalHoldsPage /> },
              { path: 'retention', element: <AdminRetentionHubPage /> },
              { path: 'retention/platform', element: <AdminPlatformRetentionPage /> },
              { path: 'retention/legal-holds', element: <AdminPlatformLegalHoldsPage /> },
              { path: 'retention/purge-result', element: <AdminPurgeResultPage /> },
              { path: 'change-requests', element: <AdminChangeRequestsPage /> },
              { path: 'change-requests/:id', element: <AdminChangeRequestDetailPage /> },
              { path: 'change-requests/:id/gitops', element: <AdminChangeRequestGitOpsPage /> },
              { path: 'change-requests/:id/dry-run', element: <AdminChangeRequestDryRunPage /> },
              { path: 'change-requests/:id/diff', element: <AdminChangeRequestDiffPage /> },
              { path: 'rbac', element: <AdminRbacPage /> },
              { path: 'security/break-glass', element: <AdminBreakGlassOpsPage /> },
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


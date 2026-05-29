import { createBrowserRouter, Navigate } from 'react-router-dom'
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
import { AdminOverviewPage } from '../pages/admin/AdminOverviewPage'
import { AdminSetupChecklistPage } from '../pages/admin/AdminSetupChecklistPage'
import { AdminSearchDiagnosticsPage } from '../pages/admin/AdminSearchDiagnosticsPage'
import { AdminIdentityOverviewPage } from '../pages/admin/AdminIdentityOverviewPage'
import { AdminSsoDiagnosticsPage } from '../pages/admin/AdminSsoDiagnosticsPage'
import { AdminScimProvisioningPage } from '../pages/admin/AdminScimProvisioningPage'
import { AdminRoleMappingDiagnosticsPage } from '../pages/admin/AdminRoleMappingDiagnosticsPage'
import { AdminBreakGlassOpsPage } from '../pages/admin/AdminBreakGlassOpsPage'
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
import { AdminNotificationDeadLetterDetailPage } from '../pages/admin/AdminNotificationDeadLetterDetailPage'
import { AdminNotificationDeadLetterRequeuePage } from '../pages/admin/AdminNotificationDeadLetterRequeuePage'
import { AdminRetentionHubPage } from '../pages/admin/AdminRetentionHubPage'
import { AdminPlatformLegalHoldsPage } from '../pages/admin/AdminPlatformLegalHoldsPage'
import { AdminPurgeResultPage } from '../pages/admin/AdminPurgeResultPage'
import { AdminRbacPage } from '../pages/admin/AdminRbacPage'
import { AdminBreakGlassRotationPage } from '../pages/admin/AdminBreakGlassRotationPage'
import { AdminChangeRequestsPage } from '../pages/admin/AdminChangeRequestsPage'
import { AdminChangeRequestDetailPage } from '../pages/admin/AdminChangeRequestDetailPage'
import { AdminChangeRequestGitOpsPage } from '../pages/admin/AdminChangeRequestGitOpsPage'
import { AdminChangeRequestDryRunPage } from '../pages/admin/AdminChangeRequestDryRunPage'
import { AdminChangeRequestDiffPage } from '../pages/admin/AdminChangeRequestDiffPage'
import { AdminGate, Protected } from './route-guards'

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


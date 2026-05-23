# Notebook Platform: Final React Implementation Handoff

## 1. Product Information Architecture
The Notebook Platform is structured into two distinct high-level domains managed by a unified `AppShell`.

### Main Navigation Structure
*   **User Domain**: Focused on productivity and knowledge management.
    *   **Workspaces**: Hierarchical entry point.
    *   **Search**: Global discovery (Command-Palette & Full-page).
    *   **Notifications**: Activity stream and alerts.
    *   **Settings**: Personal preferences and offline sync status.
*   **Admin Domain**: Secure GitOps and operations console.
    *   **Diagnostics**: Identity, SSO, and system health.
    *   **GitOps**: YAML-based change management and PR automation.
    *   **Security**: RBAC, Break-glass, and Audit logs.
    *   **Operations**: Notification dead-letter and retention management.

### Architecture Optimization
*   **Consolidation**: The "Search Discovery Dashboard" (SCREEN_38) and "Global Search" (SCREEN_13) have been unified to prevent navigational redundancy.
*   **Isolation**: User content is strictly separated from Admin diagnostics; Admin views never render raw note content, only metadata and audit references.

---

## 2. React Component Inventory

### Core Layout & Navigation
*   `AppShell`: Root container with responsive layout management.
*   `SideNavBar`: Desktop persistent sidebar with domain switching.
*   `TopNavBar`: Contextual breadcrumbs, global search trigger, and profile.
*   `MobileBottomNav`: Primary destinations for 360px-767px viewports.
*   `ResponsiveSidebar`: Collapsible icon-only variant for tablet.

### Common UI Elements
*   `StatusBadge`: Unified colors: `success`, `warning`, `error`, `info`, `neutral`.
*   `RiskBadge`: High-visibility indicators for high-stakes admin actions.
*   `EmptyState`: Standardized (Icon + Title + Description + CTA).
*   `DetailDrawer`: Sliding side panel for metadata and secondary actions.
*   `ConfirmationModal`: Required MFA/Reason gate for destructive actions.

### Editor & Collaboration
*   `NoteEditorLayout`: Canvas + floating toolbars + contextual sidebars.
*   `ConflictResolutionDialog`: Side-by-side diff for manual merge resolution.
*   `CommentThread`: Nested interaction component for document collaboration.

### Admin & GitOps
*   `GitOpsDiffViewer`: High-complexity code comparison engine.
*   `DataTable`: Virtualized table for high-volume logs (Audit, SCIM).
*   `ResponsiveDataCard`: Mobile-optimized alternative for `DataTable`.
*   `DryRunPanel`: Impact preview simulator for configuration changes.

---

## 3. Critical Flow Map

### User Journeys
1.  **Onboarding**: `Welcome` (SCREEN_27) → `Create Workspace` (SCREEN_14) → `First Notebook` (SCREEN_25) → `First Note` (SCREEN_48).
2.  **Discovery**: `Global Search Overlay` (SCREEN_13) → `Search Results` (SCREEN_18) → `Preview Drawer` → `Editor`.
3.  **Conflict Management**: `Offline Edit` → `Sync Error` → `Merge Resolution UI`.

### Admin Journeys
1.  **Security Audit**: `Admin Dashboard` → `Audit Table` → `Detail Drawer` (SCREEN_46).
2.  **GitOps PR**: `RBAC Request` → `Side-by-side Diff` (SCREEN_35) → `Dry-run` → `MFA Challenge` → `Create PR`.
3.  **Identity Recovery**: `SSO Diagnostics` (SCREEN_40) → `SCIM Sync Log` → `Manual Trigger`.

---

## 4. Responsive & Accessibility Rules

### Responsive Strategy
*   **Tables**: All `DataTable` instances must switch to `ResponsiveDataCard` below 768px.
*   **Diffs**: Unified mode is mandatory for Mobile/Tablet; Side-by-side is Desktop-only.
*   **Navigation**: Bottom navigation replaces the sidebar on mobile.

### Accessibility Checklist
*   **Keyboard**: Diff viewer must support chunk-to-chunk navigation via `Tab`.
*   **Color Blindness**: All status indicators (Added/Removed/High Risk) must use icons/symbols in addition to color.
*   **Screen Readers**: `aria-live` regions required for "Auto-saving" and "Sync complete" notifications.

---

## 5. Implementation Risks
1.  **Diff Engine Performance**: Rendering large YAML diffs with syntax highlighting in React requires optimization (e.g., memoization of lines).
2.  **Offline State Complexity**: Managing the transition between local IndexedDB storage and server-side synchronization during multi-user conflicts.
3.  **Dynamic Permissions**: App-wide conditional rendering based on granular RBAC roles (Admin vs. User vs. Restricted).
4.  **Dense Data Rendering**: Performance of the Audit and Notification logs on mobile viewports.

---

## 6. Security & Privacy Guardrails
*   **No Raw Secrets**: Mask all tokens, private keys, and API keys in UI.
*   **MFA-Required Gate**: Any "Apply" or "Create PR" action targeting Staging/Production must trigger the MFA modal.
*   **Privacy Isolation**: Admin tools display document IDs and hashes, never plaintext content or PII from user notes.

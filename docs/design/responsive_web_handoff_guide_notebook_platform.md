# Notebook Platform: Responsive Web Handoff Guide

## 1. Responsive Layout Rules
The Notebook Platform uses a fluid, grid-based layout that adapts to screen real estate while maintaining core functionality.

*   **Desktop (1024px+):** Full persistent sidebar (240px) + content area with optional right-side detail panels (320px-400px).
*   **Tablet (768px - 1023px):** Collapsible sidebar (icons-only or hidden with hamburger menu). Content area utilizes more horizontal space. Detail panels convert to full-height drawers.
*   **Mobile (360px - 767px):** Sidebar is hidden behind a hamburger menu or replaced by a **MobileBottomNav** for primary destinations. Content area is single-column. Admin pages are optimized for viewing and critical actions only.

## 2. Breakpoints
| Device Range | Width | Layout Strategy |
| :--- | :--- | :--- |
| **Mobile** | 360px – 767px | Single column, Bottom Nav, Stacked Cards |
| **Tablet** | 768px – 1023px | Icons sidebar, Centered content, Drawers |
| **Desktop** | 1024px – 1439px | Persistent sidebar, Liquid content, Side Panels |
| **Large Desktop** | 1440px+ | Persistent sidebar, Max-width content (1280px), Side Panels |

## 3. Component Responsiveness
| Component | Desktop Behavior | Mobile Behavior |
| :--- | :--- | :--- |
| **Sidebar** | Persistent (240px) | Drawer / Bottom Nav |
| **Topbar** | Global search + Profile | App title + Search icon + Hamburger |
| **DataTable** | Multi-column table | **ResponsiveDataCard** list |
| **Modals** | Centered (various widths) | Full-screen sheet or Bottom sheet |
| **Drawers** | Slide-in from right | Full-screen overlay |
| **Status Cards** | Multi-column grid | Single-column stack |
| **Diff Preview** | Side-by-side (YAML) | Unified / Inline (Scrollable) |

## 4. Table-to-Card Patterns
Dense tables (Audit, RBAC, Workspace Members, Offline Drafts) must transform into **ResponsiveDataCards** on mobile.

**Card Anatomy:**
*   **Header:** Primary Title (e.g., Member Name or Note Title) + Status Badge (top right).
*   **Body:** 2-3 key metadata points (e.g., Role, Last Active) in a small font.
*   **Footer:** Primary Action button + "More" menu for secondary actions.
*   **Interaction:** Tap card to open the **DetailDrawer**.

## 5. Note Editor Responsive Behavior
*   **Desktop:** Sidebar (Explorer) + Canvas + Side Panel (Comments/History).
*   **Tablet:** Collapsible Explorer, Side Panel becomes a slide-over.
*   **Mobile:** 
    *   Full-screen writing canvas.
    *   Toolbar docked above keyboard.
    *   Comments/Versions accessible via bottom-sheet triggers in the header.
    *   Sync status reduced to a small icon indicator next to the title.

## 6. Admin Responsive Behavior
*   **Safety First:** High-risk actions (Purge, Revoke, Role Change) always trigger a full-screen confirmation modal with required reason text.
*   **View-centric:** Default to read-only diagnostics. 
*   **GitOps:** Complex YAML editing shows a "Recommended on Desktop" banner, but allows emergency small edits via a focused code editor.

## 7. Modal & Drawer Behavior
*   **Desktop/Tablet:** `max-w-md` or `max-w-lg` centered modals.
*   **Mobile:** 
    *   Standard Modals -> Bottom Sheets (standardized height).
    *   Destructive/Complex Actions -> Full-screen Overlays with fixed footer buttons.

## 8. React Component Inventory (Naming Suggestions)
*   `AppShell`: Root layout manager handling sidebars and viewports.
*   `ResponsiveSidebar`: Adapts from persistent to icon-only to hidden.
*   `MobileBottomNav`: Primary nav for mobile viewports.
*   `DataTable` / `ResponsiveDataCard`: The table-to-card toggle component.
*   `StatusCard`: Adaptive cards for dashboard metrics.
*   `DetailDrawer`: Contextual information container.
*   `ConfirmationModal`: Standardized gate for high-risk actions.
*   `NoteEditorLayout`: Managing the multi-panel editor state.

## 9. Accessibility Notes
*   **Focus Management:** Modals must use focus traps. Drawers must return focus to the trigger on close.
*   **Touch Targets:** Minimum 44x44px for all mobile interactive elements.
*   **Contrast:** Maintain AA standards across all status badges and labels.
*   **Reduced Motion:** Animations (drawers/modals) should respect `prefers-reduced-motion`.

## 10. Design System Cleanup (Unified Patterns)
*   **Badges:** Unified into `neutral`, `success`, `warning`, `error`, `info`.
*   **Elevation:** Level 1 (Cards), Level 2 (Modals), Level 3 (Drawers).
*   **Spacing:** Consistent use of 4px baseline (4, 8, 12, 16, 24, 32, 48).

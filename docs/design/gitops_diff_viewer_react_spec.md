# GitOps YAML Diff Viewer: React Component Specification

## 1. Component Architecture
High-level breakdown of the React component tree for the GitOps module.

### Core Components
*   **`GitOpsDiffViewer`**: Root container managing diff state (unified vs. side-by-side) and file selection.
*   **`ChangedFileList`**: (Desktop) Sidebar list of files; (Mobile) Stacked cards or dropdown selector.
*   **`DiffHeader`**: Contains YAML path breadcrumbs, mode toggle, and primary actions (Create PR, Copy).
*   **`YamlDiffBlock`**: The code rendering engine. Handles line numbers, syntax highlighting, and change indicators.
*   **`GitOpsActionBar`**: Sticky footer for mobile; floating or top-aligned for desktop.

### Specialized UI
*   **`GitOpsWarningCallout`**: Standardized alert for high-stakes context (e.g., RBAC warnings).
*   **`DiffLegend`**: Key for symbols (+/-) and colors to support accessibility.
*   **`GitOpsErrorState`**: Full-page or inline component for handling API failures (e.g., Mapping Not Found).

## 2. Responsive Behavior Matrix

| Feature | Desktop | Tablet | Mobile |
| :--- | :--- | :--- | :--- |
| **Diff Mode** | Side-by-side (default) | Unified (default) | Unified only |
| **File Navigation** | Persistent Sidebar | Collapsible Panel | Bottom Sheet / Cards |
| **Code Display** | Liquid width | Horizontal scroll | Horizontal scroll (forced) |
| **Actions** | Top-right toolbar | Top-right toolbar | Sticky bottom bar |
| **Breadcrumbs** | Full path strings | Truncated path | Compact chips |

## 3. Data Schema: RBAC Override
Example JSON structure for the RBAC diff component.
```json
{
  "file": "admin-rbac-overrides.yaml",
  "changes": [
    {
      "type": "addition",
      "content": {
        "userId": "usr_9921",
        "role": "Admin",
        "action": "GRANT",
        "reasonRef": "REQ-8F92A1",
        "requestedBy": "a.smith",
        "status": "APPROVED_FOR_APPLY"
      }
    }
  ]
}
```

## 4. Accessibility Implementation
*   **Screen Readers**: Use `aria-label="added line"` or `aria-label="removed line"` on diff markers.
*   **Contrast**: Ensure red/green backgrounds meet WCAG AA contrast ratios against text; use `+` and `-` markers for color-blind users.
*   **Keyboard**: `Tab` key cycles through changed chunks; `Enter` to expand collapsed context.

## 5. Error & Safety Patterns
*   **MFA Gate**: Any PR creation for `staging` or `production` environments triggers a `ConfirmationModal` with required MFA challenge.
*   **Impact Preview**: Standardize a "Dry-run" status badge that updates in real-time as filters are applied.

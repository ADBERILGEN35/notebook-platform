# Frontend Responsive Polish (Faz 46)

## Breakpoint strategy

- `mobile`: `<640px`
- `tablet`: `640-1023px`
- `desktop`: `>=1024px`
- `wide`: `>=1280px`

## Layout behavior

- Desktop: persistent left sidebar + main content + right panel (where applicable).
- Tablet/mobile: sidebar opens as overlay drawer (`Menu` button).
- Note details panel (comments/versions/info) becomes right drawer on non-desktop.

## Key UX updates

- Topbar compact mode on mobile (`Search` link + compact create action + notification bell).
- Notification dropdown uses bottom-sheet style on mobile.
- Search input gets sticky treatment on small screens.
- Notifications page adds collapsible filter controls on mobile.
- Admin audit page uses event cards on mobile and table on larger viewports.

## Accessibility and keyboard behavior

- Drawer supports `Escape` close.
- Overlay click closes drawer.
- Toggle buttons include explicit labels/test ids:
  - `sidebar-toggle`
  - `mobile-sidebar`
  - `right-panel-toggle`
  - `mobile-right-panel`

## Known limitations

- Focus trap is minimal (close button is focused on open, but full tab-loop trap is not yet implemented).
- No advanced gesture interactions for drawers/bottom sheets.
- Responsive tuning for every BlockNote edge case may still need incremental passes.

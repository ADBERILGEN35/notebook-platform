# Frontend Design System (App)

Kaynak tasarım: [design/notebook_platform_design_system/DESIGN.md](design/notebook_platform_design_system/DESIGN.md).

## Token’lar (Tailwind)

`frontend/tailwind.config.ts` — Faz 138’de auth için eklenen semantic renkler AppShell’de kullanılır:

- Surfaces: `surface`, `surface-container-*`, `on-surface`, `on-surface-variant`
- Primary: `primary`, `primary-container`, `primary-fixed`
- Outline: `outline`, `outline-variant`
- Error: `error-container`
- Typography: `font-display`, `text-headline-sm`, `text-body-md`, `text-label-md`
- Shadow: `shadow-card`, `shadow-auth-card`

## Layout pattern’leri

| Pattern | Bileşen | Kullanım |
|---------|---------|----------|
| App chrome | `AppShell` | Authenticated routes |
| Page title | `PageHeader` | Hub, settings, admin |
| Section | `PageSection` | Hub quick actions, lists |
| Content width | `ResponsiveContent` | Hub main column |
| Empty / load / error | `EmptyState`, `LoadingState`, `ErrorState` | API-driven pages |

## App shell spacing

- Desktop sidebar: `w-72`, border `outline-variant`
- Main padding: `p-3 sm:p-4 lg:p-6`
- Mobile: bottom nav `pb-16`, drawer overlay

## Bileşen durumları

- **Focus:** `focus-visible:outline` + `outline-primary` (keyboard)
- **Active nav:** `bg-primary-fixed` + `text-primary`
- **Cards:** `rounded-xl`, `border-outline-variant`, hover `border-primary/30`

## Accessibility

- Landmarks: `aside` (SideNav), `header` (TopNav), `main#main-content`
- `aria-label` on navigation regions and icon-only controls
- Workspace list: `aria-current` on active workspace button
- Empty states: `aria-label` on section title

## Auth vs app

- Auth: `features/auth/components/AuthShell` — centered card, marketing layout
- App: `AppShell` — full viewport, sidebar + topbar

Harici CDN Tailwind/Material Symbols kullanılmaz; fontlar `index.html` Google Fonts link ile.

## Faz 140 patterns

| Pattern | Components |
|---------|------------|
| Settings page | `SettingsSection`, `InfoRow`, `DangerZoneCard` |
| Members | `MemberRow`, `RoleBadge`, invite/role modals |
| Editor chrome | `NoteEditorShell`, `NoteEditorHeader`, `SharePanel` |
| History | `VersionHistoryPanel`, `ActivityTimelinePanel`, `TimelineItem` |
| Access | `AccessDeniedState`, `AccessRequestPanel` |
| Notifications | `NotificationCenterPage` + `SectionCard` inbox |

## Faz 141 patterns

| Pattern | Components |
|---------|------------|
| Command search | `GlobalSearchOverlay`, Ctrl+K in AppShell |
| Search results | `SearchResultCard`, `SearchFilterPanel`, `SearchPreviewDrawer` |
| Discovery | `DiscoveryCard`, saved/recent in localStorage |
| Settings chrome | `SettingsLayout`, `SettingsNav`, `PreferenceToggle` |
| Security | `SessionRow`, `SecurityMethodCard`, `ConfirmActionModal` |
| Offline | `SyncHealthCard`, `LocalDataUsageCard`, `ClearLocalDataDialog` |

# Faz 141 Özeti: Search, User Settings & Offline Sync Foundation

Spec: [phase-141.md](phase-141.md). Önceki: [phase-140-summary.md](phase-140-summary.md).

## 1. Yapılanlar

Kullanıcı alanının kalan ana ekranları: command-palette global search, search results (`?q=`), discovery dashboard, settings layout (profile / security / notifications / offline sync). AppShell’de Ctrl+K, SideNav’da Discover. Sync conflict: mevcut `SyncConflictResolutionModal` (Faz 140) korundu, duplicate yok.

## 2. Route’lar

| Route | Sayfa |
|-------|--------|
| `/app/search` | `SearchResultsPage` |
| `/app/search/discover` | `SearchDiscoveryPage` |
| `/app/settings` | `UserSettingsPage` |
| `/app/settings/security` | `AccountSecurityPage` |
| `/app/settings/notifications` | `NotificationPreferencesPage` |
| `/app/settings/sync` | `OfflineSyncDiagnosticsPage` |

Auth, admin, Faz 138–140 route’ları korundu.

## 3. Component’ler

**Search:** `GlobalSearchOverlay`, `SearchResultCard`, `SearchFilterPanel`, `SearchPreviewDrawer`, `DiscoveryCard`, `useRecentSearches`, `useSavedSearches`

**Settings:** `SettingsLayout`, `SettingsNav`, `PreferenceToggle`, `SessionRow`, `SecurityMethodCard`

**Sync:** `SyncHealthCard`, `LocalDataUsageCard`, `ClearLocalDataDialog`

## 4. Davranış

| Alan | API / kaynak |
|------|----------------|
| Search | `searchNotes` |
| Notifications prefs | `getNotificationPreferences` / `patch` |
| Security | `logout`, `revokeAll`, MFA APIs |
| Offline | `getOfflineSyncDiagnostics`, local draft/cache helpers |
| Recent/saved search | localStorage (dev fixture değil, kullanıcı verisi) |

## 5. Backend / production

| Kural | Durum |
|-------|--------|
| Backend değişikliği | **Yok** |
| Production feature flag | **Açılmadı** |

## 6. Security / privacy

- Sessions/tokens UI’da gösterilmez
- Search sonuçları permission-filtered açıklaması
- Recovery codes “shown once” — session token değil
- `phase-141-pages.test.tsx` JWT/Bearer regression

## 7. Test sonuçları

| Komut | Sonuç |
|-------|--------|
| `vitest run phase-141` | **PASS** (8) |
| `vitest run router.auth` | **PASS** (4) |
| `npx tsc -b` | **PASS** |

## 8. Bilinen limitler

- `SettingsPage.tsx` monolith dosyada kaldı (router artık yeni sayfaları kullanır; workspace notification policy UI tam sürüm için legacy dosyaya bakılabilir)
- Per-device session listesi API bekliyor
- Notebook filter search API’de henüz bağlı değil (UI shell)

## 9. Sonraki adım

1. Workspace notification policy UI’yi `NotificationPreferencesPage` altına taşıma
2. E2E: Ctrl+K → search → open note
3. Profile PATCH API entegrasyonu

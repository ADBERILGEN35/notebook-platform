# Faz 142 Özeti: Admin Overview + Audit + Identity/Security Diagnostics Foundation

Spec: [phase-142.md](phase-142.md). Önceki: [phase-141-summary.md](phase-141-summary.md).

## 1. Yapılanlar

Yönetici alanının temel operasyon ekranları eklendi: sistem özeti, kurulum checklist’i, admin arama/diagnostics gezintisi, identity/SSO/SCIM/role-mapping diagnostics ve birleşik break-glass ops. Mevcut `AdminAuditPage`, enterprise status ve SCIM/RBAC/break-glass API client’ları yeniden kullanıldı.

## 2. Route’lar

| Route | Sayfa |
|-------|--------|
| `/app/admin` | → `/app/admin/overview` |
| `/app/admin/overview` | `AdminOverviewPage` |
| `/app/admin/setup` | `AdminSetupChecklistPage` |
| `/app/admin/search` | `AdminSearchDiagnosticsPage` |
| `/app/admin/audit` | `AdminAuditPage` |
| `/app/admin/identity` | `AdminIdentityOverviewPage` |
| `/app/admin/identity/sso` | `AdminSsoDiagnosticsPage` |
| `/app/admin/identity/scim` | `AdminScimProvisioningPage` |
| `/app/admin/identity/role-mapping` | `AdminRoleMappingDiagnosticsPage` |
| `/app/admin/security/break-glass` | `AdminBreakGlassOpsPage` |

Auth, kullanıcı alanı ve legacy admin route’ları korundu.

## 3. Component’ler

**Shared:** `AdminPageShell`, `AdminHealthCard`, `AdminOverviewCard`, `AdminRiskBadge`, `AdminRunbookLink`, `AdminDiagnosticPanel`

**Overview:** `AdminSetupChecklist`, `buildSetupChecklistItems`

**Search:** `AdminSearchPanel`

**Identity:** `IdentityStatusCard`, `SsoDiagnosticCard`, `ScimProvisioningCard`, `RoleMappingTable`, `RoleMappingWarningCard`

**Security:** `BreakGlassStatusCard`, `BreakGlassRevocationSummary`, `maskSessionId`

**Audit (presentational):** `AuditEventTable`, `AuditEventDetailDrawer`

## 4. Davranış

| Alan | Kaynak |
|------|--------|
| Health / warnings | `useEnterpriseStatus` |
| Pending CR özeti | `listChangeRequests('PENDING')` (flag + permission) |
| SCIM sync | `scim-diagnostics-api` |
| Role mapping | `admin-rbac-api` |
| Break-glass | `admin-break-glass-api` + `BreakGlassActiveSessionsPanel` |
| Admin search | Kategori gezintisi (sunucu araması değil) |

Kapalı modüller: feature flag ile nav gizleme veya `EmptyState` / açıklayıcı disabled metin.

## 5. Backend / production

| Kural | Durum |
|-------|--------|
| Backend değişikliği | **Yok** |
| Yeni endpoint | **Yok** |
| Production feature flag | **Açılmadı** |

## 6. Security / privacy

- Audit metadata `maskSensitiveMetadata` ile drawer’da
- SSO diagnostics secret-safe (JWKS/redirect sunucu tarafı mesajı)
- SCIM: uyarı/özet alanları; raw IdP claim yok
- Break-glass: masked session id; `BreakGlassActiveSessionsPanel` jtiMasked
- `phase-142-pages.test.tsx` secret regression

## 7. Admin guard

`AdminGate` + `canShowAdminNavigation` + sayfa içi `hasPlatformPermission` / flag kontrolleri korundu; bypass yok.

## 8. Test sonuçları

| Komut | Sonuç |
|-------|--------|
| `vitest run phase-142` | **PASS** (9) |
| `vitest run router.auth` | **PASS** (5) |
| `npx tsc -b` | **PASS** |

## 9. Bilinen limitler

- SSO JWKS/redirect canlı sonuçları için ayrı admin API yok (enterprise snapshot + runbook metni)
- `AdminAuditPage` henüz `AuditEventTable`/`AuditEventDetailDrawer` ile refactor edilmedi
- Admin search sunucu tarafı birleşik arama değil (kategori shell)
- `BreakGlassActiveSessionsPanel` session id artık `maskSessionId` ile gösterilir

## 10. Sonraki adım

1. Faz 143: Admin change requests + enterprise write UX polish
2. Audit sayfasını paylaşılan table/drawer bileşenlerine taşıma
3. E2E admin overview → audit → identity smoke

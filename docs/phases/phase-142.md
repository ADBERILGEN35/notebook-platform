# Faz 142: Frontend Admin Overview + Audit + Identity/Security Diagnostics Foundation

## 1. Yapılanlar

Admin domain temel ekranları: overview, setup checklist, search & diagnostics shell, identity/SSO/SCIM/role-mapping diagnostics, break-glass ops. Stitch tasarımları görsel referans; mevcut gateway API’ler ve feature flag’ler kullanıldı. **Backend değişikliği yok.**

Önceki: [phase-141-summary.md](phase-141-summary.md).

## 2. Backend değişiklikleri

Yok. Yeni endpoint uydurulmadı.

## 3. Frontend

| Alan | Konum |
|------|--------|
| Shared admin UI | `features/admin/shared/` — `AdminPageShell`, `AdminHealthCard`, `AdminRunbookLink`, … |
| Overview / setup | `features/admin/overview/` |
| Search shell | `features/admin/search/AdminSearchPanel.tsx` |
| Identity | `features/admin/identity/` |
| Security | `features/admin/security/` — break-glass status, `maskSessionId` |
| Audit components | `features/admin/audit/AuditEventTable.tsx`, `AuditEventDetailDrawer.tsx` |
| Sayfalar | `pages/admin/Admin*Page.tsx` (overview, setup, search, identity, SSO, SCIM, role-mapping, break-glass ops) |

## 4. Routes

| Route | Page |
|-------|------|
| `/app/admin` | redirect → `/app/admin/overview` |
| `/app/admin/overview` | `AdminOverviewPage` |
| `/app/admin/setup` | `AdminSetupChecklistPage` |
| `/app/admin/search` | `AdminSearchDiagnosticsPage` |
| `/app/admin/audit` | `AdminAuditPage` (mevcut) |
| `/app/admin/identity` | `AdminIdentityOverviewPage` |
| `/app/admin/identity/sso` | `AdminSsoDiagnosticsPage` |
| `/app/admin/identity/scim` | `AdminScimProvisioningPage` |
| `/app/admin/identity/role-mapping` | `AdminRoleMappingDiagnosticsPage` |
| `/app/admin/security/break-glass` | `AdminBreakGlassOpsPage` |

Legacy: `/app/admin/enterprise/*`, `/app/admin/rbac`, notifications/retention route’ları korundu.

## 5. API kaynakları

| Ekran | API |
|-------|-----|
| Overview / identity / SSO | `GET /admin/enterprise/status` |
| Setup checklist | enterprise status türetilmiş adımlar |
| SCIM | `/admin/identity/scim/*` (mevcut diagnostics client) |
| Role mapping | `/admin/rbac/users`, `/admin/rbac/overrides/status` |
| Break-glass | `/admin/break-glass/events`, `/admin/security/break-glass/sessions` |
| Audit | mevcut `audit-api` / mock (Faz 77+) |

## 6. Security

- `metadata-mask` audit drawer’da
- Session id masked break-glass events tablosunda
- Token/JWT/secret/raw IdP claim UI’da yok
- `AdminGate` + permission guard korundu

## 7. Feature flags

Mevcut flag isimleri (`ADMIN_UI_ENABLED`, `SCIM_COMPATIBILITY_DIAGNOSTICS_ENABLED`, `BREAK_GLASS_*`, `ADMIN_RBAC_UI_ENABLED`, …). **Production’da flag açılmadı.**

## 8. Tests

`phase-142-pages.test.tsx`, `router.auth.test.tsx` (admin route registry)

## 9. Sonraki

Admin change-request workflow polish, dedicated SSO live diagnostics API (backend), audit table refactor to shared components, E2E admin smoke.

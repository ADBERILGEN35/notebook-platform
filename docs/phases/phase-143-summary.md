# Faz 143 Özeti: Admin Change Requests + GitOps Diff Viewer Foundation

Spec: [phase-143.md](phase-143.md). Önceki: [phase-142-summary.md](phase-142-summary.md).

## 1. Yapılanlar

Admin change request ve GitOps akışları modüler sayfalara taşındı: liste (status tabs, badges, GitOps indicator), detay (timeline, approval gate, sanitized payload), dry-run preview, PR state card, YAML diff viewer (unified + side-by-side), RBAC override diff + PLATFORM_ADMIN uyarısı.

## 2. Route’lar

| Route | Sayfa |
|-------|--------|
| `/app/admin/change-requests` | `AdminChangeRequestsPage` |
| `/app/admin/change-requests/:id` | `AdminChangeRequestDetailPage` |
| `/app/admin/change-requests/:id/gitops` | `AdminChangeRequestGitOpsPage` |
| `/app/admin/change-requests/:id/dry-run` | `AdminChangeRequestDryRunPage` |
| `/app/admin/change-requests/:id/diff` | `AdminChangeRequestDiffPage` |

`/app/admin/enterprise/change-requests` legacy alias (aynı list component).

## 3. Component’ler

`ChangeRequestStatusBadge`, `SeverityBadge`, `OperationTypeBadge`, `GitOpsStateBadge`, `ChangeRequestTimeline`, `ApprovalGatePanel`, `DryRunWarningList`, `GitOpsPrStateCard`, `GitOpsDiffViewer`, `DiffLine`, `DiffFileHeader`, `RbacOverrideDiffPanel`, `ChangeRequestListTable`, `GitOpsDisabledBanner`, `maskDiffLineContent`, `dry-run-storage` (sessionStorage).

## 4. Feature flag disabled

- Write kapalı: `GitOpsDisabledBanner reason="write"`
- GitOps PR kapalı: banner + liste üstü bilgi
- RBAC GitOps kapalı: RBAC role request’lerde ayrı banner / liste “RBAC GitOps proposals off”

## 5. Backend / production

| Kural | Durum |
|-------|--------|
| Backend değişikliği | **Yok** |
| Production feature flag | **Açılmadı** |

## 6. Security / privacy

- Diff satırlarında secret masking
- Structured payload / impact JSON sanitize (IdP claim redacted, sensitive keys masked)
- Masked user id in timeline
- External PR `rel="noreferrer"`
- Regression: `phase-143-pages.test.tsx`

## 7. Admin guard

`AdminGate` + `PERM_CHANGE_REQUEST_*` + `isEnterpriseAdminWriteEnabled()` korundu.

## 8. Test sonuçları

| Komut | Sonuç |
|-------|--------|
| `vitest run phase-143` | **PASS** (8) |
| `vitest run AdminEnterpriseChangeRequests` | **PASS** (4) |
| `vitest run router.auth` | **PASS** (5) |
| `npx tsc -b` | **PASS** |

## 9. Bilinen limitler

- Change request by-id API yok; detay `listChangeRequests(ALL)` ile resolve
- Dry-run sonucu `sessionStorage` ile sayfalar arası (sekme kapatılınca kaybolur)
- Side-by-side diff test ortamında `mode` prop ile; responsive auto henüz hook’suz
- Onay/red modalları detay sayfasında; liste satırında hızlı approve kaldırıldı

## 10. Sonraki faz

Faz 144 önerisi: Admin notifications/retention governance UI polish veya E2E change-request → GitOps PR smoke.

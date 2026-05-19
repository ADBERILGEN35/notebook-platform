# Faz 143: Frontend Admin Change Requests + GitOps Diff Viewer Foundation

## 1. Yapılanlar

Change request listesi, detay, GitOps dry-run, PR state, YAML diff viewer ve RBAC override diff paneli. Mevcut `change-requests-api` kullanıldı. **Backend değişikliği yok.**

Önceki: [phase-142-summary.md](phase-142-summary.md).

## 2. Backend

Yok.

## 3. Routes

| Route | Sayfa |
|-------|--------|
| `/app/admin/change-requests` | `AdminChangeRequestsPage` |
| `/app/admin/change-requests/:id` | `AdminChangeRequestDetailPage` |
| `/app/admin/change-requests/:id/gitops` | `AdminChangeRequestGitOpsPage` |
| `/app/admin/change-requests/:id/dry-run` | `AdminChangeRequestDryRunPage` |
| `/app/admin/change-requests/:id/diff` | `AdminChangeRequestDiffPage` |

Legacy: `/app/admin/enterprise/change-requests` → aynı list UI.

## 4. Components

`features/admin/change-requests/` — badges, timeline, diff viewer, PR state, RBAC panel, dry-run storage.

## 5. Security

- `maskDiffLineContent`, `sanitizeStructuredPreview`
- PR links `rel="noreferrer"`
- No tokens in UI

## 6. Feature flags

`ENTERPRISE_ADMIN_WRITE_ENABLED`, `ENTERPRISE_GITOPS_PR_ENABLED`, `GITOPS_RBAC_ROLE_REQUESTS_ENABLED` — production açılmadı.

## 7. Tests

`phase-143-pages.test.tsx`, güncellenmiş `AdminEnterpriseChangeRequestsPage.test.tsx`

## 8. Sonraki

E2E approve → dry-run → PR; get-by-id API when available.

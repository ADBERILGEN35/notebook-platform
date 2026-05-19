# Faz 144 Özeti: Admin Retention + Notification Operations + Legal Holds Foundation

Spec: [phase-144.md](phase-144.md). Önceki: [phase-143-summary.md](phase-143-summary.md).

## 1. Yapılanlar

Notification analytics (delivery health kartları), dead-letter queue/detail/requeue workflow, notification retention overview, platform retention governance hub, platform legal holds, destructive purge confirmation shell, purge result summary. Mevcut admin API client’ları kullanıldı; route’lar modüler sayfalara ayrıldı.

## 2. Route’lar

| Route | Sayfa |
|-------|--------|
| `/app/admin/notifications/analytics` | `AdminNotificationAnalyticsPage` |
| `/app/admin/notifications/dead-letter` | `AdminNotificationDeadLetterPage` |
| `/app/admin/notifications/dead-letter/:eventId` | `AdminNotificationDeadLetterDetailPage` |
| `/app/admin/notifications/dead-letter/:eventId/requeue` | `AdminNotificationDeadLetterRequeuePage` |
| `/app/admin/notifications/retention` | `AdminNotificationRetentionPage` |
| `/app/admin/retention` | `AdminRetentionHubPage` |
| `/app/admin/retention/platform` | `AdminPlatformRetentionPage` |
| `/app/admin/retention/legal-holds` | `AdminPlatformLegalHoldsPage` |
| `/app/admin/retention/purge-result` | `AdminPurgeResultPage` |

Legacy korundu: `/app/admin/notifications/legal-holds`, `/app/admin/retention/platform`.

## 3. Component’ler

**Notifications:** `AdminMetricCard`, `DeadLetterEventTable`, `DuplicateRiskBadge`, `RequeueEligibilityChecklist`, `OperationalRunbookLink`, `notification-ops-utils`, `use-dead-letter-event`.

**Retention:** `RetentionTargetTable`, `RetentionWarningChip`, `RetentionServiceSummaryCard`, `LegalHoldCard`, `PurgeConfirmationDialog`, `PurgeResultSummary`, `purge-result-storage`.

## 4. Feature flag disabled

| Alan | Davranış |
|------|----------|
| Notification analytics / dead-letter / retention UI | Disabled banner + boş shell |
| Destructive purge | `PurgeConfirmationDialog` execute disabled; `NOTIFICATION_RETENTION_PURGE_*` kapalı |
| Platform legal holds | Platform retention flag kapalıysa create/release shell gizli |
| Production flags | **Açılmadı** |

## 5. Backend / production

| Kural | Durum |
|-------|--------|
| Backend değişikliği | **Yok** |
| Yeni endpoint | **Yok** |
| Production feature flag | **Açılmadı** |
| Destructive purge çalıştırma (faz kapsamı) | UI yalnızca flag+perm ile; testlerde purge flag **kapalı** |

## 6. Security / privacy

- Analytics: aggregate-only; channel/type breakdown; no PII
- Dead-letter detail: sanitized JSON; masked recipient hash; no raw body
- Requeue: dry-run-first, reason, MFA copy, duplicate risk badge
- Retention: target counts only; legal-hold blocked chips
- Legal holds: truncated reason; masked owner id
- Regression: `phase-144-pages.test.tsx` (no JWT/Bearer/full hash)

## 7. Admin guard

`AdminGate`, `AdminLayout` permission + feature-flag nav, `PERM_NOTIFICATIONS_*` / `PERM_RETENTION_*` korundu.

## 8. Test sonuçları

| Komut | Sonuç |
|-------|--------|
| `vitest run phase-144` | **PASS** (10) |
| `vitest run Notification` | **PASS** (14) |
| `vitest run DeadLetter` | **PASS** (2) |
| `vitest run Retention` | **PASS** (13) |
| `vitest run LegalHold` | **N/A** (filtre eşleşmedi; legal hold: `phase-144` içinde) |
| `vitest run Admin` | **PASS** (50) |
| `vitest run router.auth` | **PASS** (6) |
| `vitest run` (full) | **PASS** (231) |
| `npx tsc -b` | **PASS** |
| `check-no-secrets.sh` | **PASS** |

## 9. Bilinen limitler

- Dead-letter by-id API yok; detail `list` penceresinden resolve
- Duplicate risk tabloda yalnızca dry-run sonrası detail/requeue sayfalarında
- Purge sonucu `sessionStorage` — sekme kapatılınca kaybolur
- Platform retention sayfasında legal-hold bölümü hâlâ inline (ayrı `/retention/legal-holds` sayfası eklendi)

## 10. Sonraki faz

Faz 145 önerisi: E2E notification ops smoke; dead-letter get-by-id API; platform destructive purge dry-run UI (API hazır olduğunda).

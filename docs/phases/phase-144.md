# Faz 144: Frontend Admin Retention + Notification Operations + Legal Holds Foundation

## 1. Yapılanlar

Notification analytics, dead-letter queue/detail/requeue workflow, notification retention overview, platform retention governance, legal holds, destructive purge confirmation shell, purge result summary. Mevcut admin API client’ları kullanıldı. **Backend değişikliği yok.**

Önceki: [phase-143-summary.md](phase-143-summary.md).

## 2. Backend

Yok. Yeni endpoint uydurulmadı.

## 3. Routes

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

Legacy: `/app/admin/notifications/legal-holds`, `/app/admin/retention/platform` korunur.

## 4. Components

`features/admin/notifications/` — `AdminMetricCard`, `DeadLetterEventTable`, `DuplicateRiskBadge`, `RequeueEligibilityChecklist`, `OperationalRunbookLink`, `notification-ops-utils`, `use-dead-letter-event`.

`features/admin/retention/` — `RetentionTargetTable`, `RetentionWarningChip`, `RetentionServiceSummaryCard`, `LegalHoldCard`, `PurgeConfirmationDialog`, `PurgeResultSummary`, `purge-result-storage`.

## 5. Security

- Aggregate-only analytics; no recipient PII
- Dead-letter detail: sanitized JSON, masked recipient hash
- Requeue: dry-run first, reason + MFA copy, duplicate risk badge
- Purge: high-friction dialog; execute disabled unless purge feature flag + permission
- Legal holds: no sensitive case documents; masked actor ids

## 6. Feature flags

`NOTIFICATION_*`, `PLATFORM_RETENTION_*`, `NOTIFICATION_RETENTION_PURGE_*` — production açılmadı.

## 7. Tests

`phase-144-pages.test.tsx`, `router.auth.test.tsx` (Faz 144 routes)

## 8. Sonraki

E2E dead-letter requeue smoke; platform purge dry-run when API expands; get-by-id dead-letter when available.

# Notification retention worker (Faz 83)

Foundation for **bounded, audited** deletion of notification subsystem rows that are safe to expire: derived analytics aggregates, terminal fanout/email/digest records, and dead-letter requeue audit rows.

**Faz 84 — legal hold:** When `NOTIFICATION_LEGAL_HOLD_ENABLED=true`, active holds **block** purge for matching targets (`blockedByLegalHold`, `purgeableCount=0`, warnings on the plan). Destructive runs skip blocked kinds; see `docs/notification-legal-hold.md` and `docs/retention-governance.md`.

**Faz 98 — platform governance:** Platform-wide retention inventory and dry-run planning are documented in `docs/platform-retention-governance.md`. Faz 98 does not call this worker and does not enable destructive purge.

## Defaults (safe)

| Setting | Default | Meaning |
|---------|---------|---------|
| `NOTIFICATION_RETENTION_WORKER_ENABLED` | `false` | Scheduled worker does nothing when off. |
| `NOTIFICATION_RETENTION_DRY_RUN_ONLY` | `true` | When worker is on, it only computes/logs plans — **no deletes**. |
| `NOTIFICATION_RETENTION_MANUAL_RUN_ENABLED` | `false` | Admin API rejects `dryRun=false` purge until enabled. |
| `NOTIFICATION_RETENTION_ADMIN_API_ENABLED` | `false` | Internal `/internal/admin/notifications/retention` returns 404 when off. |

## Worker schedule

- `NOTIFICATION_RETENTION_POLL_INTERVAL_MS` (default `86400000` = 24h) — `@Scheduled` fixed delay on `NotificationRetentionWorker`.

## Purge limits

- `NOTIFICATION_RETENTION_BATCH_SIZE` (default `1000`) — rows per JDBC batch.
- `NOTIFICATION_RETENTION_MAX_DELETE_PER_RUN` (default `10000`) — cap per worker or manual run across all targets.

## Targets (low-cardinality metrics)

| API / metric `target` | Source table(s) | Rule (summary) |
|-----------------------|-----------------|----------------|
| `notification_delivery_analytics_hourly` | `notification_delivery_analytics_hourly` | `bucket_start` older than `NOTIFICATION_ANALYTICS_RETENTION_DAYS` |
| `notification_fanout_outbox_sent` | `notification_fanout_outbox` | `status = SENT` and `sent_at` older than `NOTIFICATION_FANOUT_SENT_RETENTION_HOURS` |
| `notification_fanout_outbox_dead` | `notification_fanout_outbox` | `status = DEAD` and `coalesce(dead_at, created_at)` older than `NOTIFICATION_FANOUT_DEAD_RETENTION_DAYS` |
| `notification_dead_letter_requeue_requests` | `notification_dead_letter_requeue_requests` | `created_at` older than `NOTIFICATION_DEAD_LETTER_REQUEUE_REQUEST_RETENTION_DAYS` |
| `notification_digest_items_terminal` | `notification_digest_items` | `SENT` / `CANCELLED` only, older than `NOTIFICATION_DIGEST_SENT_RETENTION_DAYS` |
| `email_notifications_terminal` | `email_notifications` | `SENT`, `FAILED`, `CANCELLED`, `SKIPPED` with `updated_at` older than `NOTIFICATION_EMAIL_TERMINAL_RETENTION_DAYS` |

**Never** deleted by this worker: `PENDING`, `SENDING`, digest `PENDING`, in-app `user_notifications`, platform audit events, object storage.

## Faz 102 platform retention dry-run counts (ayrı endpoint)

Faz 102 platform-wide retention governance contract'ı için **ayrı** bir endpoint ekler: `GET /internal/admin/retention/notification/plan` (scope `internal:admin:retention:read`, audience `notification-service`). Bu, bu sayfadaki Faz 83 worker'ından ve `/internal/admin/notifications/retention/*` endpoint'lerinden bağımsızdır — worker schedule, purge limits, manuel run davranışı ve Faz 84 plan response'u **değişmez**. Platform endpoint'i aggregate-only, dry-run-only, legal-hold aware count görünürlüğü sağlar ve gateway `/admin/retention/platform/plan` tarafından identity registry planına merge edilir. Detay: [`platform-retention-governance.md`](platform-retention-governance.md) → "Faz 102 Notification-service Integration". Feature flag `NOTIFICATION_RETENTION_DRY_RUN_COUNTS_ENABLED` default `false`.

**Faz 103 — RLS production runbook:** Bu Faz 102 platform endpoint'inin production'da RLS altında güvenle açılması (dedicated retention DB role, preflight SQL, smoke script, rollback, prod checklist) [`notification-retention-rls-production-runbook.md`](notification-retention-rls-production-runbook.md)'de tanımlanır. Operatör notu: yukarıdaki Faz 83 `/internal/admin/notifications/retention/*` worker endpoint'i ile bu Faz 102 `/internal/admin/retention/notification/plan` platform endpoint'i **ayrı**dır — runbook bölüm 2 farkı netleştirir. Faz 103 production kodu değiştirmez.

## Internal API

- `GET /internal/admin/notifications/retention/plan?dryRun=true` — service JWT `internal:admin:notifications:retention:read`
- `POST /internal/admin/notifications/retention/run` — body `{ "dryRun": true|false, "target": "ALL"|apiTargetKey, "reason": "..." }`
  - `dryRun=true` (default if omitted): read scope
  - `dryRun=false`: run scope; requires `X-Admin-Actor-User-Id`, reason ≥ 10 chars, `NOTIFICATION_RETENTION_MANUAL_RUN_ENABLED=true`

## Gateway

- `GET /admin/notifications/retention/plan`
- `POST /admin/notifications/retention/run`
- Rate limit: plan = admin-audit bucket; POST run = admin-write bucket (same path for dry and destructive — MFA still enforced in controller for destructive).

## Plan fields (Faz 84)

Each target in the plan may include: `blockedByLegalHold`, `activeHoldKeys`, `purgeableCount` (0 when blocked), `targetWarnings` (e.g. expired `expiresAt` while still `ACTIVE`).

## Metrics (Micrometer)

- `notification.retention.plan` — tagged `target`
- `notification.retention.purge.deleted` — tagged `target`
- `notification.retention.purge.failures` — tagged `target`
- `notification.retention.worker.runs` — tagged `result` (`success` | `dry_run_only` | `failure`)
- `notification.retention.last.run.timestamp` — epoch millis gauge

## Audit (notification-service DB)

- `ADMIN_NOTIFICATION_RETENTION_PLAN_VIEWED`, `ADMIN_NOTIFICATION_RETENTION_DRY_RUN`, `ADMIN_NOTIFICATION_RETENTION_PURGE_*`, worker completion/failure types — no raw notification bodies in metadata.

## Frontend

- Route: `/app/admin/notifications/retention`
- Flags: `FRONTEND_NOTIFICATION_RETENTION_ENABLED`, optional `FRONTEND_NOTIFICATION_RETENTION_PURGE_ENABLED` for destructive modal.

See also: [`notification-retention-policy.md`](notification-retention-policy.md).

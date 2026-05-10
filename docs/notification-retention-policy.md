# Notification retention policy (Faz 83)

## Principles

1. **No silent mass deletion in production defaults** — worker off or dry-run-only; manual purge off until `NOTIFICATION_RETENTION_MANUAL_RUN_ENABLED=true`.
2. **Active delivery never touched** — rows in retry/sending/pending paths stay until terminal and aged out.
3. **Aggregates first-class** — hourly analytics are derived; deleting old buckets does not remove user-visible inbox state.
4. **Dead-letter history** — fanout `DEAD` rows use `NOTIFICATION_FANOUT_DEAD_RETENTION_DAYS` (see `notification.fanout.dead-retention-days`). Keep ≥ operational review window (warn if &lt; 90 days in planner).
5. **Requeue audit** — `notification_dead_letter_requeue_requests` tracks admin actions; retention is independent of fanout row lifecycle.

## Cutoff sources

| Data class | Configuration |
|------------|----------------|
| Analytics hourly | `notification.analytics.retention-days` / `NOTIFICATION_ANALYTICS_RETENTION_DAYS` |
| Fanout SENT | `notification.fanout.sent-retention-hours` / `NOTIFICATION_FANOUT_SENT_RETENTION_HOURS` |
| Fanout DEAD | `notification.fanout.dead-retention-days` / `NOTIFICATION_FANOUT_DEAD_RETENTION_DAYS` |
| Requeue requests | `notification.retention.dead-letter-requeue-request-retention-days` |
| Digest terminal | `notification.retention.digest-sent-retention-days` |
| Email terminal | `notification.retention.email-terminal-retention-days` |

## Legal hold (Faz 84)

Notification-scoped **legal holds** can block purge for matching targets regardless of age; see `docs/notification-legal-hold.md` and `docs/retention-governance.md`.

## Out of scope (Faz 83–84)

- Per-tenant retention, **platform-wide** legal hold, user notification inbox purge, audit-event purge, object storage lifecycle, automatic destructive enablement in prod without review.

## Rollout checklist

1. Enable admin API + UI in **non-prod**; verify plan counts.
2. Keep `NOTIFICATION_RETENTION_DRY_RUN_ONLY=true` on worker; observe metrics/logs.
3. Optionally enable worker deletes with small `NOTIFICATION_RETENTION_MAX_DELETE_PER_RUN`.
4. Enable manual purge only for break-glass with MFA and `NOTIFICATION_RETENTION_MANUAL_RUN_ENABLED=true`.

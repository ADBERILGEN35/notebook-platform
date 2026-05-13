# Notification legal hold (Faz 84)

Notification-scoped **legal holds** block destructive retention purge for configured targets. This is **not** a platform-wide hold, audit-event hold, or object-storage lifecycle control.

Faz 98 adds a separate platform-wide governance model (`docs/platform-legal-hold-model.md`). Existing notification legal holds remain the enforcement layer for notification purge until a future bridge is explicitly approved.

## Feature flags

| Layer | Variable | Default |
|-------|----------|---------|
| notification-service | `NOTIFICATION_LEGAL_HOLD_ENABLED` | `false` |
| notification-service | `NOTIFICATION_LEGAL_HOLD_ADMIN_API_ENABLED` | `false` |
| notification-service | `NOTIFICATION_LEGAL_HOLD_MAX_ACTIVE` | `50` |
| notification-service | `NOTIFICATION_LEGAL_HOLD_ADMIN_API_ENABLED` (internal) | same as admin API env |
| frontend | `FRONTEND_NOTIFICATION_LEGAL_HOLD_ENABLED` | `false` |

Service JWT allow-list must include `internal:admin:notifications:legal-hold:read` and `internal:admin:notifications:legal-hold:write` for gateway proxy calls.

## Data model

Table `notification_legal_holds`: unique `hold_key`, `scope`, `reason`, `status` (`ACTIVE` \| `RELEASED`), actor fields, optional `expires_at`, `metadata` JSON (no notification payloads).

**Expiration:** an `expires_at` in the past does **not** auto-release. The planner may warn; only an explicit **release** clears the block.

## Scopes

| Scope | Blocks purge for |
|-------|------------------|
| `ALL_NOTIFICATION_RETENTION` | All Faz 83 retention targets |
| `FANOUT_OUTBOX` | Fanout outbox SENT + DEAD |
| `DEAD_LETTER_REQUEUE_REQUESTS` | Dead-letter requeue request rows |
| `ANALYTICS` | Hourly delivery analytics aggregates |
| `DIGEST_ITEMS` | Terminal digest items |
| `EMAIL_NOTIFICATIONS` | Terminal email notification rows |

## APIs

**notification-service (internal)**

- `GET /internal/admin/notifications/legal-holds?status=ACTIVE|RELEASED`
- `POST /internal/admin/notifications/legal-holds`
- `POST /internal/admin/notifications/legal-holds/{id}/release`

Headers: `X-Service-Authorization` (Bearer service JWT), `X-Admin-Actor-User-Id` (UUID) on writes; optional `X-Admin-Actor-Email` on create.

**api-gateway**

- `GET /admin/notifications/legal-holds`
- `POST /admin/notifications/legal-holds`
- `POST /admin/notifications/legal-holds/{id}/release`

RBAC: `admin:notifications:legal-hold:read` (list), `admin:notifications:legal-hold:write` (create/release). Writes use the same admin-write MFA gate as other high-impact admin mutations when enforced. CSRF double-submit applies for cookie transport.

## Audit

`ADMIN_NOTIFICATION_LEGAL_HOLD_*`, `NOTIFICATION_RETENTION_BLOCKED_BY_LEGAL_HOLD` — metadata includes hold keys, scope, target keys; **no raw notification content**.

## Metrics (Micrometer / Prometheus)

- `notification_legal_holds_active{scope}`
- `notification_legal_hold_created_total{scope}`
- `notification_legal_hold_released_total{scope}`
- `notification_retention_blocked_by_legal_hold_total{target}`

No `userId` labels.

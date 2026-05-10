# Notification dead-letter requeue (Faz 82)

Operational tooling for **durable fanout outbox** rows in `DEAD` status. This is **not** email resend, digest replay, or per-user inspection.

## Scope (MVP)

- **Source:** `notification_fanout_outbox` only (`source=fanout`, `status=DEAD`).
- **Actions:** list (privacy-safe fields), dry-run, admin requeue back to `PENDING`.
- **Out of scope:** raw SSE payload in UI, email/digest resend, bulk “requeue all”, automation.

**Faz 84:** Admin **legal holds** can block retention purge for fanout outbox (and related notification retention targets); see `docs/notification-legal-hold.md`.

## APIs

| Layer | Method | Path |
|-------|--------|------|
| api-gateway | GET | `/admin/notifications/dead-letter?source=fanout&status=DEAD&...` |
| api-gateway | POST | `/admin/notifications/dead-letter/{id}/requeue/dry-run` |
| api-gateway | POST | `/admin/notifications/dead-letter/{id}/requeue` |
| notification-service | GET | `/internal/admin/notifications/dead-letter` |
| notification-service | POST | `/internal/admin/notifications/dead-letter/{id}/requeue/dry-run` |
| notification-service | POST | `/internal/admin/notifications/dead-letter/{id}/requeue` |

Internal calls use service JWT scopes:

- `internal:admin:notifications:dead-letter:read` — list + dry-run
- `internal:admin:notifications:dead-letter:requeue` — requeue

Gateway forwards `X-Admin-Actor-User-Id` (JWT `sub`) for audit attribution.

## RBAC

| Permission | Use |
|------------|-----|
| `admin:notifications:dead-letter:read` | List + dry-run |
| `admin:notifications:dead-letter:requeue` | Requeue |

**Roles (identity):** `PLATFORM_OBSERVABILITY_VIEWER` — read only; `PLATFORM_SECURITY_ADMIN` — read + requeue; `PLATFORM_ADMIN` — all.

**MFA:** Requeue uses the same **admin-write MFA** gate as other high-impact gateway mutations (`ADMIN_WRITE_MFA_REQUIRED` when gateway policy requires step-up).

## Guardrails

- Only `DEAD` rows requeue; `attemptCount` unchanged; `requeue_count` incremented (cap `NOTIFICATION_DEAD_LETTER_MAX_REQUEUE_COUNT`).
- **Reason** required (min length enforced server-side).
- **Idempotency:** `idempotencyKey` in POST body; table `notification_dead_letter_requeue_requests` unique on `(source, dead_letter_id, idempotency_key)`.
- **Privacy:** responses expose `recipientUserIdHash` (SHA-256 of pepper + user id), sanitized `lastErrorSummary`, no JSON payload.

## Configuration

| Env | Service | Purpose |
|-----|---------|---------|
| `NOTIFICATION_DEAD_LETTER_ADMIN_ENABLED` | notification-service | Enables internal dead-letter API (404 when false) |
| `NOTIFICATION_DEAD_LETTER_MAX_REQUEUE_COUNT` | notification-service | Max admin requeues per row |
| `NOTIFICATION_DEAD_LETTER_PAGE_MAX_SIZE` | notification-service | Page size cap |
| `NOTIFICATION_DEAD_LETTER_RECIPIENT_HASH_PEPPER` | notification-service | Optional pepper for recipient hash (set in prod) |
| `GATEWAY_ADMIN_ENTERPRISE_NOTIFICATION_DEAD_LETTER_PATH` | api-gateway | Upstream path prefix |
| `FRONTEND_NOTIFICATION_DEAD_LETTER_ENABLED` | frontend | Runtime UI flag |

## Audit (notification-service DB)

Event types on `notification_audit_events`:

- `ADMIN_NOTIFICATION_DEAD_LETTER_VIEWED`
- `ADMIN_NOTIFICATION_DEAD_LETTER_REQUEUE_DRY_RUN`
- `ADMIN_NOTIFICATION_DEAD_LETTER_REQUEUED`
- `ADMIN_NOTIFICATION_DEAD_LETTER_REQUEUE_DENIED`

Gateway logs: `admin_notification_dead_letter_viewed`, `admin_notification_dead_letter_requeue_dry_run`, `admin_notification_dead_letter_requeued`.

## UI

- `/app/admin/notifications/dead-letter`
- Analytics dashboard links to dead-letter when UI + read permission are enabled.

## Related

- [`notification-durable-fanout.md`](notification-durable-fanout.md)
- [`notification-analytics-dashboard.md`](notification-analytics-dashboard.md)
- [`notification-retention-worker.md`](notification-retention-worker.md) — optional bounded purge of old `SENT` / `DEAD` outbox rows (separate from requeue)
- [`admin-permission-matrix.md`](admin-permission-matrix.md)

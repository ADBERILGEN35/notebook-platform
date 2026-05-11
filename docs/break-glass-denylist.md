# Break-glass Denylist (Phase 93)

The denylist tracks revoked break-glass token JTIs until token expiry.

## Data model

`break_glass_token_denylist`

- `jti` (unique)
- `session_id`
- `event_id`
- `revoked_by_user_id`
- `revoked_at`
- `expires_at`
- `reason`
- `source`

## Gateway behavior

- If `GATEWAY_BREAK_GLASS_DENYLIST_CHECK_ENABLED=true`, gateway queries identity internal denylist endpoint.
- Short local cache reduces repeat lookups (`GATEWAY_BREAK_GLASS_DENYLIST_CACHE_SECONDS`).
- With `GATEWAY_BREAK_GLASS_DENYLIST_FAIL_CLOSED=true`, lookup failure blocks break-glass request.

## Operational note

- Expired denylist rows can be purged by SQL/runbook.
- Short token TTL remains primary containment guardrail.

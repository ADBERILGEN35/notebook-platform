# Platform Legal Hold Model (Faz 98)

Faz 98 notification legal hold davranisini kirmadan platform-wide governance abstraction ekler. Yeni model identity-service uzerinde merkezi admin governance verisi olarak tutulur.

## Table

`platform_legal_holds`

- `id`
- `hold_key`
- `scope`: `ALL_PLATFORM`, `CONTENT`, `IDENTITY`, `AUDIT`, `NOTIFICATION`, `WORKSPACE`, `USER`, `NOTE`
- `scope_ref_id` nullable
- `reason`
- `status`: `ACTIVE`, `RELEASED`
- `created_by_user_id`, `created_at`
- `released_by_user_id`, `released_at`, `release_reason`
- `expires_at` nullable

## Blocking semantics

- `ALL_PLATFORM` blocks every legal-hold-supported target.
- `CONTENT` blocks content/search targets.
- `IDENTITY` blocks identity targets.
- `AUDIT` blocks audit/security targets.
- `NOTIFICATION` blocks notification targets.
- `WORKSPACE`, `USER`, `NOTE` are future scoped holds; Faz 98 dry-run maps them to their domain class only.
- Expired holds do **not** auto-release. An ACTIVE hold with past `expiresAt` still blocks and produces a warning.
- Release requires a reason and admin-write MFA gate at the gateway.

## Compatibility with notification legal holds

Existing notification legal holds stay in notification-service and continue to block Faz 83 notification retention purge. Platform legal holds are a governance layer for platform-wide dry-run visibility. Future phases can bridge platform `NOTIFICATION` holds into notification-service before any destructive notification purge.

## Security

- Responses do not include hold reason text.
- Metadata records reason presence, scope, and hold key, not raw content or audit metadata.
- No purge endpoint is added in Faz 98.

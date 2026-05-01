# Refresh Token Revocation

Faz 18 implements authenticated refresh token logout and revoke-all in identity-service.

## Implemented Endpoints

### `POST /auth/logout`

Requires `Authorization: Bearer <accessToken>` and a body:

```json
{"refreshToken":"<refresh-token>"}
```

Rules:

- access token must have `token_type=access`
- refresh token must have `token_type=refresh`
- refresh token `sub` must match access token `sub`
- token hash must exist in DB
- already revoked token is idempotent and returns `204 No Content`
- successful revoke sets `revoked_at`, `revoked_reason=USER_LOGOUT` and `revoked_by_user_id`

### `POST /auth/revoke-all`

Requires `Authorization: Bearer <accessToken>`. Body is optional:

```json
{"reason":"ACCOUNT_SECURITY"}
```

Allowed reasons:

- `USER_REVOKE_ALL`
- `ACCOUNT_SECURITY`

Response:

```json
{"revokedCount":3}
```

Only active, unexpired tokens for the authenticated user are counted. Already revoked or expired
tokens are not counted.

## Security Notes

- Refresh token plaintext is never stored.
- Audit metadata may include internal token UUIDs, `revokedCount` and reason, but never token
  plaintext or token hashes.
- Revoking refresh tokens does not immediately invalidate already issued access tokens. Access token
  TTL remains the compensating control until a future blacklist/introspection design exists.
- Refresh token reuse on `/auth/refresh` returns `401 INVALID_REFRESH_TOKEN` and records
  `REFRESH_TOKEN_REUSE_REJECTED`. Faz 18 does not automatically revoke all sessions on reuse because
  false positives and rotation races need a stronger incident policy.

## Future Work

- `GET /auth/sessions` for user-visible device/session management.
- Automatic invalidation through `password_changed_at` or `credentials_changed_at`.
- Optional access token blacklist or introspection for immediate access-token revocation.

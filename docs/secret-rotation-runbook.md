# Secret Rotation Runbook

## Internal API Token

1. Generate a new high-entropy token in the secret manager.
2. Add it as `INTERNAL_API_TOKEN_SECONDARY` in workspace-service.
3. Deploy workspace-service and verify both primary and secondary tokens are accepted.
4. Set content-service `WORKSPACE_INTERNAL_API_TOKEN_PRIMARY` to the new token.
5. Deploy content-service and verify internal permission calls succeed.
6. Promote the new token to workspace-service `INTERNAL_API_TOKEN_PRIMARY`.
7. Keep the old token as `INTERNAL_API_TOKEN_SECONDARY` only for the rollback window.
8. Remove the old token from secondary after traffic and error metrics are clean.

Legacy `INTERNAL_API_TOKEN` and `WORKSPACE_INTERNAL_API_TOKEN` are local/dev fallback only and must not be used in prod.

## JWT Keys

Normal rotation:

1. Start with `JWT_KEYS_ACTIVE_KID=key-1`; JWKS returns `key-1`.
2. Add `key-2` as another configured signing key, but keep `active-kid=key-1`.
3. Deploy identity-service; JWKS now returns `key-1` and `key-2`.
4. Wait for gateway JWKS caches to fetch the expanded set.
5. Change `JWT_KEYS_ACTIVE_KID=key-2`.
6. Deploy identity-service; new access and refresh tokens carry `kid=key-2`.
7. Keep `key-1` configured until all old access tokens and refresh tokens signed by `key-1` expire.
8. Remove `key-1` from config and secrets.

Emergency rotation:

- Remove the compromised key from identity-service config and JWKS immediately.
- Gateway rejects access tokens with the removed `kid`.
- identity-service rejects refresh tokens with the removed `kid`.
- This invalidates affected sessions; without revoke-all/session management this is the intended containment behavior.

Refresh token impact:

- Access-token compatibility only requires the old key until access token TTL expires.
- Refresh-token compatibility requires the old key for the full refresh token TTL unless those refresh tokens are revoked.

## DB Password

- Rotate through the database platform or secret manager.
- Update `DB_PASSWORD` in each affected service.
- Restart service instances so Hikari/connection pools reconnect with the new password.
- Coordinate with migration jobs because Flyway uses the same datasource credentials.

## Redis Password

- Enable Redis auth in the deployment platform.
- Update `REDIS_PASSWORD` for api-gateway.
- Restart gateway instances.
- Expect rate-limit state to be temporarily unavailable if Redis restarts.

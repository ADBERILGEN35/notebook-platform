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

## Service JWT Internal Keys

Normal rotation for `content-service -> workspace-service`:

1. Generate a new RSA keypair for content-service internal service JWT signing.
2. Add the new public key to workspace-service trusted config and keep the old key trusted.
3. Deploy workspace-service first.
4. Change content-service `INTERNAL_SERVICE_JWT_ACTIVE_KID` and private key path to the new key.
5. Deploy content-service; new internal calls carry the new `kid`.
6. Keep the old public key trusted until all old service JWTs expire. With the default 60 second TTL,
   this window is short.
7. Remove the old trusted public key after metrics confirm success.

With Kubernetes External Secrets, update the provider values first, wait for the ExternalSecret
refresh or force a sync according to the operator runbook, then restart affected pods. Kubernetes
Secret volume updates are eventually reflected on disk, but application key loaders read at startup,
so a rollout restart is the operationally safe path.

Emergency rotation:

- Remove the compromised trusted public key from workspace-service config and redeploy.
- Deploy content-service with a new private key and `kid`.
- During recovery, `INTERNAL_AUTH_MODE=dual` plus static token can be used as a temporary rollback
  path if static tokens are still provisioned.

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
- This invalidates affected sessions. Prefer `/auth/revoke-all` for user/session compromise; key removal remains the containment behavior for signing-key compromise.

Refresh token impact:

- Access-token compatibility only requires the old key until access token TTL expires.
- Refresh-token compatibility requires the old key for the full refresh token TTL unless those refresh tokens are revoked.

## DB Password

- Rotate through the database platform or secret manager.
- Update the provider value that feeds `identity-db-password`, `workspace-db-runtime-password`,
  `workspace-db-migration-password`, `content-db-runtime-password` or
  `content-db-migration-password`.
- Restart service instances so Hikari/connection pools reconnect with the new password.
- Coordinate with migration jobs because Flyway uses the same datasource credentials.

## Redis Password

- Enable Redis auth in the deployment platform.
- Update the provider value that feeds `redis-password`.
- Restart gateway instances.
- Expect rate-limit state to be temporarily unavailable if Redis restarts.

## Kubernetes Rollout Behavior

External Secrets Operator updates the generated Kubernetes Secret on `refreshInterval`. Pods are not
guaranteed to restart automatically. The Helm chart includes `checksum/config` and `checksum/secret`
annotations for rendered values, but provider-side changes do not necessarily change Helm output.

After rotating a secret, use one of:

- `kubectl rollout restart deployment/<service> -n <namespace>`
- a Secret reloader controller
- a Helm upgrade that changes a rendered annotation

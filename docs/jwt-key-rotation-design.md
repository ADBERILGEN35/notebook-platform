# JWT Key Rotation Design

Faz 10 implements JWKS/kid support while keeping the Faz 9 single-key env/file fallback.

## Key Config Model

Preferred production config:

```yaml
jwt:
  keys:
    active-kid: prod-key-1
    signing-keys:
      - kid: prod-key-1
        private-key-path: /run/secrets/jwt_private_key_1
        public-key-path: /run/secrets/jwt_public_key_1
      - kid: prod-key-2
        private-key-path: /run/secrets/jwt_private_key_2
        public-key-path: /run/secrets/jwt_public_key_2
```

Equivalent env names:

- `JWT_KEYS_ACTIVE_KID`
- `JWT_KEYS_SIGNING_KEYS_0_KID`
- `JWT_KEYS_SIGNING_KEYS_0_PRIVATE_KEY_PATH`
- `JWT_KEYS_SIGNING_KEYS_0_PUBLIC_KEY_PATH`
- `JWT_KEYS_SIGNING_KEYS_1_KID`
- `JWT_KEYS_SIGNING_KEYS_1_PRIVATE_KEY_PATH`
- `JWT_KEYS_SIGNING_KEYS_1_PUBLIC_KEY_PATH`

Backward compatibility remains:

- `JWT_PRIVATE_KEY` / `JWT_PRIVATE_KEY_PATH`
- `JWT_PUBLIC_KEY` / `JWT_PUBLIC_KEY_PATH`

When legacy single-key config is used, the default `kid` is `primary` unless `JWT_KEYS_ACTIVE_KID` overrides it.

## Token Header

identity-service signs access and refresh tokens with the active signing key.

JWT header:

- `alg=RS256`
- `kid=<jwt.keys.active-kid>`

Existing claims are unchanged:

- access: `sub`, `email`, `token_type=access`, `iat`, `exp`
- refresh: `sub`, `jti`, `token_type=refresh`, `iat`, `exp`

## JWKS Endpoint

identity-service exposes:

```text
GET /.well-known/jwks.json
```

The response is an RFC 7517 JWK Set containing public RSA keys only:

- `kty=RSA`
- `kid`
- `use=sig`
- `alg=RS256`
- `n`
- `e`

Private fields such as `d`, `p`, `q`, `dp`, `dq` and `qi` are never returned.

## Gateway Validation

api-gateway supports:

```text
JWT_JWKS_URI=http://identity-service:8081/.well-known/jwks.json
```

If `JWT_JWKS_URI` is configured, Spring Security/Nimbus fetches JWKS and validates by `kid`. If not configured, the existing `JWT_PUBLIC_KEY` / `JWT_PUBLIC_KEY_PATH` static fallback remains. Production accepts the fallback for compatibility, but JWKS is the preferred deployment mode.

Unknown `kid`, invalid signature and malformed JWTs return `401 INVALID_ACCESS_TOKEN`. Expired tokens return `401 EXPIRED_ACCESS_TOKEN`. Non-access token types return `401 INVALID_TOKEN_TYPE`.

## Refresh Token Validation

identity-service does not call its JWKS endpoint for refresh validation. It reads the refresh token header `kid`, selects the matching configured public key and validates locally. Unknown `kid` becomes `INVALID_REFRESH_TOKEN`.

Because refresh tokens have a longer TTL, retired keys must remain configured in identity-service until all refresh tokens signed with that key expire or are revoked.

## Rotation Strategy

1. Current state: `active-kid=key-1`, JWKS contains `key-1`.
2. Add `key-2` to config while keeping `active-kid=key-1`; JWKS contains `key-1` and `key-2`.
3. Wait for gateway JWKS caches to observe `key-2`.
4. Switch `active-kid=key-2`; new access and refresh tokens are signed with `key-2`.
5. Keep `key-1` configured until old access tokens expire and old refresh tokens are no longer valid.
6. Remove `key-1`; JWKS contains only `key-2`.

Emergency rotation can remove a compromised key immediately, but any access or refresh token signed by that key stops validating. Without a revoke-all session mechanism, this is intentionally disruptive.

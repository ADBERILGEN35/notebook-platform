# Break-glass revocation staging evidence (Faz 123)

Schema: `break-glass-revocation-evidence-v1`

## Artifacts

| File | Purpose |
|------|---------|
| `break-glass-revocation-evidence.json` | Sanitized drill result |
| `break-glass-revocation-summary.md` | CR / Step Summary handoff |

## Result values

| Result | Meaning |
|--------|---------|
| `passed` | Revoke + gateway `BREAK_GLASS_TOKEN_REVOKED` verified |
| `skipped` | Staging secrets not configured |
| `failed` | Revoked token still accepted or drill step failed |
| `privacy-failure` | Forbidden pattern in artifacts |

## Forbidden in artifacts

Bearer, eyJ, access_token, emergency token literals, raw jti, Authorization, password, secret values.

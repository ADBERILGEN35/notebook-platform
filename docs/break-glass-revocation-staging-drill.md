# Break-glass revocation staging drill (Faz 123)

## Purpose

Prove the staging chain: **issue break-glass JWT → list active sessions → revoke → gateway rejects revoked jti** with sanitized evidence for change requests.

## Prerequisites (staging only)

| Flag | Required |
|------|----------|
| `BREAK_GLASS_ENABLED` | `true` |
| `BREAK_GLASS_REVOCATION_ENABLED` | `true` |
| `GATEWAY_BREAK_GLASS_DENYLIST_CHECK_ENABLED` | `true` |
| `GATEWAY_BREAK_GLASS_ADMIN_ALLOWED` | `true` (drill uses admin routes) |
| `BREAK_GLASS_ALLOW_ADMIN_WRITE` | Keep **`false`** unless a separate approved write is required; revoke uses dedicated revoke permission + MFA |

## GitHub secrets / variables

| Name | Required | Description |
|------|----------|-------------|
| `BREAK_GLASS_STAGING_API_BASE_URL` | Live drill | Staging gateway base URL |
| `BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN` | Live drill | Platform admin JWT (MFA-verified for revoke) |
| `BREAK_GLASS_STAGING_EMERGENCY_TOKEN` | One of | Static break-glass login secret |
| `BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN` | One of | Pre-issued break-glass JWT |
| `BREAK_GLASS_STAGING_TEST_ENDPOINT` | Optional | Default `/admin/enterprise/status` |

## Automated drill

```bash
export BREAK_GLASS_STAGING_API_BASE_URL=https://staging.example.com
export BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN=...
export BREAK_GLASS_STAGING_EMERGENCY_TOKEN=...
bash scripts/security/run-break-glass-revocation-evidence.sh
```

Artifacts:

- `break-glass-revocation-evidence.json`
- `break-glass-revocation-summary.md`

GitHub Actions: **Break-glass Revocation Readiness** → `workflow_dispatch` (not on PR).

## Manual UI checklist

1. Enterprise **Security** → Break-glass card shows posture flags.
2. Enable `FRONTEND_BREAK_GLASS_REVOCATION_UI_ENABLED` on staging frontend.
3. **Active break-glass sessions** table loads (no token/JWT column).
4. Revoke modal: reason under 10 characters → validation error.
5. Revoke with valid reason → success message; token status **REVOKED**.
6. Using the same break-glass JWT on an admin GET → **401** `BREAK_GLASS_TOKEN_REVOKED`.
7. Attach workflow artifacts + this checklist to the change request.

## Expected results

| Case | `result` | Exit |
|------|----------|------|
| Secrets missing | `skipped` | 0 |
| Revoke + gateway reject | `passed` | 0 |
| Revoked token still accepted | `failed` | 5 |
| Forbidden pattern in artifact | `privacy-failure` | 3 |

See `scripts/security/break-glass-revocation-evidence-formats.md`.

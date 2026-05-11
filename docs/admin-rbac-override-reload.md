# Admin RBAC override reload (Faz 89)

This document describes **operational reload** of the mounted `admin-rbac-overrides.yaml` manifest in **identity-service**. It complements [admin-rbac-runtime-overrides.md](./admin-rbac-runtime-overrides.md) and [admin-rbac-override-manifest.md](./admin-rbac-override-manifest.md).

## Goals

- **Manual reload** is the primary, predictable path (`POST /admin/rbac/overrides/reload` via gateway, proxied to identity internal `POST /internal/admin/rbac/overrides/reload`).
- **No Git live fetch** — only the mounted file on the identity pod is read.
- **Last-known-good (LKG)** keeps the previous successful snapshot when a reload fails (when `ADMIN_RBAC_OVERRIDES_LAST_KNOWN_GOOD_ENABLED=true`, default).
- **Checksum** (`sha256:…`) and manifest **version** are tracked for visibility; **raw YAML is never returned** to browsers or public APIs.

## Configuration (identity-service)

| Env / property | Default | Purpose |
|----------------|---------|---------|
| `ADMIN_RBAC_OVERRIDES_RELOAD_ENABLED` | `false` | Enables reload API path. |
| `ADMIN_RBAC_OVERRIDES_LAST_KNOWN_GOOD_ENABLED` | `true` | Retain prior effective snapshot on failed reload. |
| `ADMIN_RBAC_OVERRIDES_RELOAD_FAIL_CLOSED` | `false` | When `true`, failed/missing reload throws HTTP errors instead of LKG soft-fail. |
| `ADMIN_RBAC_OVERRIDES_WATCH_ENABLED` | `false` | Optional background poll (see below). |
| `ADMIN_RBAC_OVERRIDES_WATCH_INTERVAL_SECONDS` | `30` | Poll interval for watcher. |
| `ADMIN_RBAC_OVERRIDES_WATCH_DEBOUNCE_SECONDS` | `5` | Reserved for future debounce tuning (poll-only today). |
| `ADMIN_RBAC_OVERRIDES_REQUIRE_VALID_CHECKSUM` | `false` | Reserved for future signed / expected-checksum wiring. |

## Gateway

- Permission: `admin:rbac:override:reload` (included in **PLATFORM_IDENTITY_ADMIN** and **PLATFORM_ADMIN** via `allPermissions()`).
- **MFA**: same **admin-write MFA** gate as other high-impact admin mutations when MFA is not `off`.
- **CSRF**: applies for cookie transport (double-submit filter).
- **Rate limit**: `POST /admin/rbac/overrides/reload` uses the **admin-write** Redis bucket.

## Service JWT scope

Identity internal reload requires scope: `internal:admin:rbac:overrides:reload` (separate from `internal:admin:rbac:read`). Add it to `AUDIT_ADMIN_SERVICE_JWT_ALLOWED_SCOPES` / gateway signing configuration alongside existing internal scopes.

## Audits & metrics (identity)

Audits (no raw YAML, no full reason text — metadata uses counts / flags):

- `ADMIN_RBAC_OVERRIDES_RELOAD_REQUESTED`
- `ADMIN_RBAC_OVERRIDES_RELOAD_SUCCEEDED` / `ADMIN_RBAC_OVERRIDES_RELOAD_FAILED`
- `ADMIN_RBAC_OVERRIDES_LAST_KNOWN_GOOD_USED`

Metrics include `admin_rbac_overrides_reload_total{result}`, `admin_rbac_overrides_last_known_good_used_total`, `admin_rbac_overrides_reload_duration_seconds`, gauges for current assignment counts and last reload timestamp.

## Failure modes

| Scenario | LKG on | LKG off |
|----------|--------|---------|
| Invalid YAML / schema errors | Keep prior effective snapshot; HTTP 200 with `result: FAILED` unless `RELOAD_FAIL_CLOSED` | Replace effective with failed snapshot |
| Missing file on reload | Keep prior if previously loaded | Failed unloaded state |
| Reload feature disabled | `403` `ADMIN_RBAC_OVERRIDES_RELOAD_DISABLED` | same |

## Optional watcher

When `ADMIN_RBAC_OVERRIDES_WATCH_ENABLED=true`, a single-thread scheduler compares the on-disk **SHA-256** with the effective checksum and triggers reload only if the service had a **successful prior load** (avoids tight loops on never-loaded failure). Operators should still prefer **manual reload** after GitOps promotion.

## Frontend

- Feature flag: `FRONTEND_ADMIN_RBAC_OVERRIDES_RELOAD_ENABLED` → `ADMIN_RBAC_OVERRIDES_RELOAD_ENABLED` in `runtime-config.js`.
- Reload button requires `admin:rbac:override:reload` and the UI flag; reason **≥ 10** characters.

## Rollback

Revert the ConfigMap / volume mount to the previous manifest revision, then run **manual reload** (or rely on watcher if enabled). LKG retains the last good effective assignments until a successful reload.

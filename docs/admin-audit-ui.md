# Admin / Audit UI (Faz 42/43)

Operational visibility landing in the SPA for engineering and security responders. Phase 42 ships a **UI
foundation**: routes, mocked audit source adapter, UX for filters/table/detail drawer, pagination, masking,
and routing-level access guards.

## Goal

Expose the Faz 23 audit-query contract ergonomically **without granting service JWTs to browsers**.

Backend truth remains `GET /internal/audit-events` on each owning service (`identity-service`,
`workspace-service`, `content-service`) with scope `internal:audit:read` on the service JWT ([see audit query
reference](audit-query-api.md)).

## Frontend Capabilities

- Routes inside the authenticated shell: `/app/admin`, `/app/admin/audit`, `/app/admin/audit/:eventId`.
- Sidebar + Settings shortcuts when gated access passes.
- Source selector modeled as synthetic `AuditEvent.source`:
  `identity`, `workspace`, `content`.
- Filters matching query params naming from the audit contract (excluding server-only validation such as max
  90-day retention — enforced only when the backend proxy arrives).
- Deep-linkable URLs with `?source=workspace&eventType=...` etc.
- Metadata JSON rendered as plaintext `JSON.stringify` with **second-line** key masking in the drawer.

## Modes (`FRONTEND_AUDIT_API_MODE`)

| Mode | Behaviour |
|---|---|
| `mock` (local default) | `audit-mock-api.ts` yields deterministic paging fixtures; safe for demos + Playwright |
| `real` | Calls gateway `GET /admin/audit-events` and uses platform-admin auth + server-side service JWT proxying (Faz 43). |

Production rollout should enable UI only with gateway admin allowlist/role config.

### Why no service JWT in the frontend?

OAuth-style user sessions must not elevate to internal service JWTs embedded in SPA bundles or
localStorage. That widens blast radius across all internal audits. Faz 43 now enforces user-admin
checks in gateway and maps approved requests to server-side service JWT calls.

## Access Model (MVP Flags)

Runtime / Vite knobs:

| Key | Meaning |
|---|---|
| `ADMIN_UI_ENABLED` / `VITE_ADMIN_UI_ENABLED` | Master switch for routing + sidebar entry |
| `ADMIN_UI_DEV_OPEN` / `VITE_ADMIN_UI_DEV_OPEN` | **Trusted dev + mock mode only**: bypass privileged role gate |
| Privileged JWT roles observed from `/auth/me` (`ADMIN`, `ROLE_ADMIN`, `PLATFORM_ADMIN`) | Platform admin placeholders until real RBAC exists |

When disabled, navigating to `/app/admin` redirects away; tampering URLs shows `PermissionDenied` if flags allow route but RBAC denies.

## Security & Privacy UX

Backend sanitizes audit metadata ([audit-query-api.md](audit-query-api.md)). The SPA applies additional masking
consistent with those substring rules (`password`, `token`, `secret`, …). Never interpolate metadata as HTML.

## Testing

- Vitest suites for masking, filter schema/date validation, mock pagination, URL round-trip.
- Playwright `e2e/admin-audit.spec.ts` swaps `runtime-config.js` plus minimal `auth/signup`, `auth/me`, and workspace list routes so admin gates open **without** a live gateway while still exercising bearer-style session bootstrap.

## Export UX (Faz 48)

- Admin audit screen includes export controls (CSV/JSONL).
- Export requires `createdFrom` + `createdTo`.
- Gateway returns downloadable attachment; browser never gets internal service JWT.

## Limitations (Phase 48)

- No direct SIEM push/streaming connector in this phase (manual ops/export workflow).
- No platform-wide RBAC or admin SSO roles yet.
- `PLATFORM_ADMIN` claim model is transitional; allowlist remains rollout fallback.

## Next (Phase 44+)

Ship **platform-admin authorization + audit proxy** on `api-gateway` (or audited BFF) that:

1. Validates end-user identities with confined roles (`PLATFORM_AUDITOR`).
2. Fan-outs or streams to per-service `/internal/audit-events` via service JWTs held server-side.
3. Applies rate limiting + narrower field visibility policies per environment.


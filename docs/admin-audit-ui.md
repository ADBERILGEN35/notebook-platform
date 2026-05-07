# Admin / Audit UI (Faz 42)

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
| `real` | Calls **placeholder proxy** route `GET /api-gateway-relative /admin/audit-events`. Returns `503 AUDIT_PROXY_UNAVAILABLE` styling until gateway implements proxy (Faz 43). |

Production chart defaults set `AUDIT_API_MODE=real`; keep admin UI disabled until proxy + RBAC lands.

### Why no service JWT in the frontend?

OAuth-style user sessions must not elevate to internal service JWTs embedded in SPA bundles or localStorage.
That would widen blast radius across all internal audits. Phase 43 should supply a **narrow user-admin token**
validated by gateway and mapped to audited proxy calls server-side.

## Access Model (MVP Flags)

Runtime / Vite knobs:

| Key | Meaning |
|---|---|
| `ADMIN_UI_ENABLED` / `VITE_ADMIN_UI_ENABLED` | Master switch for routing + sidebar entry |
| `ADMIN_UI_DEV_OPEN` / `VITE_ADMIN_UI_DEV_OPEN` | **Trusted dev only**: bypass privileged role gate |
| Privileged JWT roles observed from `/auth/me` (`ADMIN`, `ROLE_ADMIN`, `PLATFORM_ADMIN`) | Platform admin placeholders until real RBAC exists |

When disabled, navigating to `/app/admin` redirects away; tampering URLs shows `PermissionDenied` if flags allow route but RBAC denies.

## Security & Privacy UX

Backend sanitizes audit metadata ([audit-query-api.md](audit-query-api.md)). The SPA applies additional masking
consistent with those substring rules (`password`, `token`, `secret`, …). Never interpolate metadata as HTML.

## Testing

- Vitest suites for masking, filter schema/date validation, mock pagination, URL round-trip.
- Playwright `e2e/admin-audit.spec.ts` swaps `runtime-config.js` plus minimal `auth/signup`, `auth/me`, and workspace list routes so admin gates open **without** a live gateway while still exercising bearer-style session bootstrap.

## Limitations (Phase 42)

- No CSV export, SIEM fan-out, or central audit aggregation service.
- No platform-wide RBAC or admin SSO roles yet.
- `real` proxy path is speculative until backend contract freezes in Phase 43.

## Next (Phase 43)

Ship **platform-admin authorization + audit proxy** on `api-gateway` (or audited BFF) that:

1. Validates end-user identities with confined roles (`PLATFORM_AUDITOR`).
2. Fan-outs or streams to per-service `/internal/audit-events` via service JWTs held server-side.
3. Applies rate limiting + narrower field visibility policies per environment.


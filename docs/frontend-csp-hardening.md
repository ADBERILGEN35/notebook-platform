# Frontend CSP Hardening (Faz 44)

Faz 44 strengthens frontend runtime security by moving from a static basic CSP header to a
configurable CSP rollout model (`disabled` / `report-only` / `enforce`) with Helm/GitOps control.

## Goals

- Reduce XSS impact in cookie-auth mode.
- Keep `runtime-config.js` external (no inline runtime bootstrap script).
- Make CSP rollout reversible by config only (no image rollback required).

## Header Model

Nginx now emits baseline security headers from generated runtime config:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: geolocation=(), camera=(), microphone=()`
- `Cross-Origin-Opener-Policy: same-origin`
- `Cross-Origin-Resource-Policy: same-origin`

CSP header name depends on mode:

- `Content-Security-Policy` (enforce)
- `Content-Security-Policy-Report-Only` (report-only)

## CSP Policy

Base directives:

- `default-src 'self'`
- `base-uri 'self'`
- `object-src 'none'`
- `frame-ancestors 'none'`
- `form-action 'self'`
- `img-src 'self' data: blob: ...`
- `font-src 'self' data: ...`
- `style-src 'self' 'unsafe-inline'`
- `script-src 'self'`
- `connect-src 'self' <FRONTEND_API_BASE_URL origin> ...`
- `worker-src 'self' blob:`
- `manifest-src 'self'`

Optional:

- `report-uri <FRONTEND_CSP_REPORT_URI>`

## Runtime Inputs

`frontend/docker-entrypoint.sh` builds CSP dynamically from:

- `FRONTEND_CSP_ENABLED`
- `FRONTEND_CSP_REPORT_ONLY`
- `FRONTEND_CSP_REPORT_URI`
- `FRONTEND_CSP_CONNECT_SRC`
- `FRONTEND_CSP_IMG_SRC`
- `FRONTEND_CSP_FONT_SRC`
- `FRONTEND_API_BASE_URL` (origin auto-added to `connect-src`)

## Rollout Recommendation

1. Dev: disabled or report-only.
2. Staging: report-only + monitor violations.
3. Fix policy/app gaps.
4. Production: short report-only period, then enforce.
5. Rollback: set `FRONTEND_CSP_ENABLED=false` or `FRONTEND_CSP_REPORT_ONLY=true`.

## XSS Surface Review (Faz 44)

- No `dangerouslySetInnerHTML` in `frontend/src`.
- Audit metadata rendered as plain JSON text.
- Runtime config loaded from external `/runtime-config.js` script (not inline JS).
- React error rendering remains text-node based.

Known limitation:

- `style-src 'unsafe-inline'` remains due to UI/editor styling requirements (Tailwind + editor stack).
  This is documented and should be revisited with stricter style nonce/hash strategy in a future phase.

## Source Maps

Vite build now supports `VITE_SOURCEMAP=true|false`.

- Dev/staging: may keep sourcemaps for debugging.
- Prod recommendation: `false`.

## Future Work (out of Faz 44 scope)

- CSP report collector/storage endpoint.
- Full SRI pipeline for assets.
- COEP strict isolation rollout.

# Frontend Deployment

Faz 40 ile frontend, backend servisleriyle ayni deployment standardina alinmistir.

## Container Model

- `frontend/Dockerfile` multi-stage build kullanir.
- Build stage: `node:22-alpine`, `npm ci`, `npm run build`.
- Runtime stage: `nginxinc/nginx-unprivileged:alpine`.
- Runtime sadece static build artefact + nginx config icerir.

## Runtime Config

Frontend tek image ile farkli ortamlarda calisir:

- runtime endpoint: `/runtime-config.js`
- env: `FRONTEND_API_BASE_URL`
- env: `FRONTEND_AUTH_TRANSPORT`
- optional admin/audit flags via the same script: `FRONTEND_ADMIN_UI_ENABLED`, `FRONTEND_ADMIN_UI_DEV_OPEN`, `FRONTEND_AUDIT_API_MODE`
- startup script: `frontend/docker-entrypoint.sh`

Config precedence:

1. `window.__NOTEBOOK_CONFIG__.API_BASE_URL` (runtime)
2. `VITE_API_BASE_URL` (build-time fallback)
3. `http://localhost:8080` (local fallback)

Auth transport runtime precedence:

1. `window.__NOTEBOOK_CONFIG__.AUTH_TRANSPORT`
2. `VITE_AUTH_TRANSPORT`
3. `bearer`

Admin / audit UI runtime precedence:

1. `window.__NOTEBOOK_CONFIG__.ADMIN_UI_ENABLED` / `ADMIN_UI_DEV_OPEN` / `AUDIT_API_MODE`
2. `VITE_ADMIN_UI_ENABLED` / `VITE_ADMIN_UI_DEV_OPEN` / `VITE_AUDIT_API_MODE`
3. safe defaults (`false`, `mock`)

- SPA fallback: `try_files $uri $uri/ /index.html`
- `/assets/*`: immutable cache (`max-age=31536000`)
- `index.html` + `runtime-config.js`: no-cache
- health endpoint: `/healthz`

Security headers:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy` minimal
- basic CSP (future phase: stricter tuning + report-only rollout)

## Helm + GitOps

Chart values:

- `frontend.enabled`
- `frontend.image.*`
- `frontend.env.FRONTEND_API_BASE_URL`
- `frontend.service.*`
- `frontend.ingress.*`
- `frontend.autoscaling.*`

Ingress model:

- primary recommendation: separate hosts
  - `app.example.com` -> frontend
  - `api.example.com` -> api-gateway
- optional future: same host path-based model

GitOps environment values include frontend overrides for dev/staging/prod.

## Smoke And E2E

- frontend smoke script: `scripts/smoke-test-frontend.sh`
- checks: `/healthz`, `/runtime-config.js`, SPA shell and `/login` route fallback
- Playwright remains full-stack/manual workflow; deployed mode should set frontend host as base URL and gateway host as API target.

## Known Limits

- Admin UI defaults off in production Helm examples until the Phase 43 audit proxy ships.
- cookie auth mode supports token-less frontend state (httpOnly cookie + CSRF model)
- legacy bearer/localStorage mode remains for backwards compatibility
- full CSP hardening not complete
- real CDN/invalidation strategy not included
- real cluster rollout and registry push outside scope

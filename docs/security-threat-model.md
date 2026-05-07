## Faz 34 Permission Snapshot Risk Notes

- Snapshot staleness is accepted as eventual consistency.
- Restricted documents always require runtime permission checks.
- Snapshot fetch and runtime permission check failures are fail-closed for restricted candidates.
- Full ACL indexing is deferred to a future phase.

# Security Threat Model

## Authentication

- Risk: Brute-force login or credential stuffing.
- Current mitigation: Gateway Redis rate limiting for auth routes; identity-service returns generic invalid credential errors.
- Remaining gap: No adaptive risk scoring or account lock policy.
- Recommended next action: Add per-account throttling and alerting before production.

## Browser Token Theft (XSS)

- Risk: `localStorage` token theft through XSS.
- Current mitigation: Faz 41 adds cookie transport (`httpOnly` access/refresh cookie) and frontend
  cookie mode that does not require localStorage tokens.
- Remaining gap: legacy bearer mode remains for backward compatibility and CSP is not fully locked
  down.
- Recommended next action: move staging/prod to cookie mode and harden CSP in a dedicated phase.

## JWT Key Management

- Risk: Private/public key mismatch or accidental dev key use in production.
- Current mitigation: identity-service emits `kid`, exposes public-only JWKS and validates refresh tokens by configured key set; gateway validates access tokens through `JWT_JWKS_URI` when configured and keeps static public-key fallback.
- Remaining gap: No access token blacklist/introspection for immediate access-token revocation.
- Recommended next action: Add operational alerts for unknown `kid`; evaluate access token blacklist or introspection only if short TTL is insufficient.

## Refresh Token Rotation

- Risk: Refresh token replay or database token leakage.
- Current mitigation: Refresh token rotation is DB-backed and stores token hashes, not plaintext tokens. Users can revoke one refresh token with `/auth/logout` or all active refresh tokens with `/auth/revoke-all`; reuse is audited as `REFRESH_TOKEN_REUSE_REJECTED`.
- Remaining gap: No device/session listing UI and no automatic revoke-all on token reuse.
- Recommended next action: Add session listing and define an incident policy for reuse-triggered mass revocation.

## Gateway Header Spoofing

- Risk: Client submits forged `X-User-Id` or `X-User-Email`.
- Current mitigation: Gateway strips identity headers and recreates them from JWT claims.
- Remaining gap: Direct service access can bypass gateway in local/dev.
- Recommended next action: Restrict service network exposure and add service authentication for all internal traffic.

## X-User-Id Trust Boundary

- Risk: Downstream services trust identity headers after gateway.
- Current mitigation: Services require `X-User-Id`; production assumes gateway/internal network boundary.
- Remaining gap: No mTLS or signed internal identity headers.
- Recommended next action: Add mTLS or gateway-signed internal headers.

## Internal API Token

- Risk: content-service calls workspace internal permission endpoints with a long-lived shared secret.
- Current mitigation: workspace-service supports `INTERNAL_AUTH_MODE=service-jwt` and validates short-lived RS256 service JWTs by issuer, audience, `kid`, `token_type=service`, expiration and endpoint scope; static tokens remain as dual/static rollback.
- Remaining gap: No mTLS transport identity and no real provider-backed External Secrets rollout.
- Recommended next action: Deploy service-jwt mode with External Secrets-backed key distribution, then add mTLS.

## Invitation Token Secrecy

- Risk: Invitation token leakage enables unauthorized join.
- Current mitigation: DB stores `token_hash`; plaintext token appears only at create time/dev response/log stub.
- Remaining gap: Dev response exposure can be misconfigured in production.
- Recommended next action: Enforce `INVITATION_EXPOSE_TOKEN_IN_RESPONSE=false` in production deployment checks.

## Content Permission Fail-Closed

- Risk: workspace-service outage accidentally grants content access.
- Current mitigation: content-service returns `503 WORKSPACE_SERVICE_UNAVAILABLE` if permission checks fail.
- Remaining gap: No cached emergency read mode.
- Recommended next action: Keep fail-closed default; evaluate short TTL cache only with explicit revocation strategy.

## Workspace Owner Safety

- Risk: Workspace becomes ownerless.
- Current mitigation: Last owner cannot be removed or downgraded.
- Remaining gap: No break-glass admin recovery flow.
- Recommended next action: Add audited platform admin recovery outside MVP.

## RLS Runtime Enforcement

- Risk: Application bug queries cross-tenant data.
- Current mitigation: workspace/content tables include `workspace_id`; application-level tenant enforcement is active; tenant-aware service methods set `app.current_workspace_id` when `APP_RLS_ENABLED=true`; non-owner runtime role SQL and opt-in FORCE RLS SQL exist.
- Remaining gap: Production still needs actual runtime credentials provisioned and strict header rollout enabled per client.
- Recommended next action: Execute the Stage 1-5 rollout in `runtime-rls-rollout.md`, starting with strict header readiness in staging.

## Actuator Exposure

- Risk: Metrics or health endpoints leak operational details.
- Current mitigation: `show-details=never`; endpoints are documented for controlled exposure.
- Remaining gap: No auth on actuator endpoints in compose.
- Recommended next action: Restrict actuator paths at gateway/network layer in production.

## CORS

- Risk: Over-broad browser origins.
- Current mitigation: Gateway CORS origins come from `CORS_ALLOWED_ORIGINS`; credentials are disabled.
- Remaining gap: Environment mistakes can broaden origins.
- Recommended next action: Validate production CORS env during deployment.

## CSRF

- Risk: Browser sends authenticated cookie requests without explicit user intent.
- Current mitigation: Faz 41 adds gateway double-submit CSRF validation for unsafe methods using
  csrf cookie + `X-CSRF-Token` header.
- Remaining gap: bypassed bootstrap paths (`/auth/login`, `/auth/signup`) still rely on rate limit
  and credential checks.
- Recommended next action: monitor 403 CSRF error rates and tune client error UX.

## Admin / Audit UI Exposure (Faz 42)

- Risk: Showing operational audit context to unintended browser sessions before hardened RBAC.
- Current mitigation: Route + navigation gates behind `ADMIN_UI_ENABLED`; mock-only adapters by default for local
  dev; Helm prod examples keep the UI disabled until the proxy arrives.
- Remaining gap: No authenticated audit proxy translating user sessions to narrowly scoped downstream queries yet.
- Recommended next action: Implement Faz 43 platform-admin role + audited gateway proxy documented in
  [`docs/admin-audit-ui.md`](admin-audit-ui.md).

## Rate Limiting

- Risk: Abuse of public or protected endpoints.
- Current mitigation: Gateway Redis-backed rate limiting by IP for public auth and user id for protected routes.
- Remaining gap: No global WAF or per-route anomaly detection.
- Recommended next action: Add edge rate limiting and alerts.

## Logging Sensitive Data Leakage

- Risk: Tokens, passwords, or PII appear in logs.
- Current mitigation: Error responses avoid stack traces; token hashes are stored; logs are structured; sensitive validation fields and audit metadata are sanitized.
- Remaining gap: Request body logging is not centrally audited.
- Recommended next action: Keep body logging disabled and add deployment-level log redaction.

## Audit Event Integrity

- Risk: Critical security/domain actions are not traceable after incidents.
- Current mitigation: identity, workspace and content services write DB-backed audit events for key actions without storing secrets in metadata; Faz 23 adds service-local internal audit query endpoints protected by service JWT scope `internal:audit:read`.
- Remaining gap: No central audit service, immutable log store, SIEM export or Admin UI.
- Recommended next action: Add export/SIEM integration and immutable archive once provider/storage choices are made.

## Search Query Privacy

- Risk: Search queries may contain sensitive user or business data.
- Current mitigation: search-service audit metadata records query length and result count, not
  plaintext query text.
- Remaining gap: No dedicated query redaction metric or privacy review workflow.
- Recommended next action: Keep plaintext search queries out of audit/log metadata and add
  aggregate metrics only.

## Email Webhook Forgery And Replay

- Risk: An attacker forges bounce/complaint events and suppresses valid recipients.
- Current mitigation: notification-service requires HMAC-SHA256 signatures for enabled webhooks and
  can require timestamp headers with `EMAIL_WEBHOOK_REQUIRE_TIMESTAMP=true`; replay outside
  `EMAIL_WEBHOOK_TOLERANCE_SECONDS` is rejected.
- Remaining gap: Provider-specific signature schemes such as SendGrid signed event webhooks and AWS
  SNS certificate validation are future adapters.
- Recommended next action: Keep generic webhook secrets in External Secrets and validate provider
  replay semantics before enabling public ingress.

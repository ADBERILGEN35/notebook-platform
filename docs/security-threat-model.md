# Security Threat Model

## Authentication

- Risk: Brute-force login or credential stuffing.
- Current mitigation: Gateway Redis rate limiting for auth routes; identity-service returns generic invalid credential errors.
- Remaining gap: No adaptive risk scoring or account lock policy.
- Recommended next action: Add per-account throttling and alerting before production.

## JWT Key Management

- Risk: Private/public key mismatch or accidental dev key use in production.
- Current mitigation: identity-service emits `kid`, exposes public-only JWKS and validates refresh tokens by configured key set; gateway validates access tokens through `JWT_JWKS_URI` when configured and keeps static public-key fallback.
- Remaining gap: No session revoke-all mechanism for emergency refresh-token invalidation.
- Recommended next action: Add operational alerts for unknown `kid` and session revocation controls.

## Refresh Token Rotation

- Risk: Refresh token replay or database token leakage.
- Current mitigation: Refresh token rotation is DB-backed and stores token hashes, not plaintext tokens.
- Remaining gap: No device/session management UI.
- Recommended next action: Add refresh token reuse monitoring and session revocation workflows.

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

- Risk: content-service calls workspace internal permission endpoints without authentication.
- Current mitigation: workspace-service validates primary/secondary internal tokens centrally for internal endpoints; content-service sends the active internal token; prod profile requires primary tokens and rejects legacy token envs.
- Remaining gap: Shared static token lacks rotation and per-service identity.
- Recommended next action: Replace with mTLS or short-lived service JWTs in the deployment phase.

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
- Recommended next action: Deploy non-owner runtime DB users, enable `APP_RLS_STRICT_WORKSPACE_HEADER` for aggregate endpoints, then enable FORCE RLS table groups after smoke tests.

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
- Current mitigation: identity, workspace and content services write DB-backed audit events for key actions without storing secrets in metadata.
- Remaining gap: No admin audit query API, immutable log store or retention policy.
- Recommended next action: Add append-only storage policy and admin audit query API with strict access control.

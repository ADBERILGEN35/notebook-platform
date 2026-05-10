## Faz 53 additions

- Scheduled audit export automation runs outside gateway app process (CronJob/script model).
- Service JWT is not exposed to frontend; scheduled auth must use secret-backed machine credentials.
- Archive integrity requires manifest + SHA256 validation before SIEM/restore workflows.
- WORM behavior is provider-managed (S3 Object Lock / GCS retention lock / Azure immutable blob),
  not application-enforced.

## Faz 71 additions

- Backend merge analysis receives local/base content snapshots; raw payload must not be written to logs.
- Merge analysis endpoint requires edit permission, preventing read-only/comment-only merge probing.
- Conservative analyze-only mode reduces integrity risk from unintended silent server merges.

## Faz 72 additions

- Merge apply endpoint remains explicit user action; backend does not perform silent merge saves.
- Idempotency storage excludes raw note content (stores hash/status/result pointers only).
- Audit metadata for merge apply excludes raw content payloads.

## Faz 73 additions

- Merge observability counters/timers avoid user/note/workspace label cardinality and PII leakage.
- Frontend merge analytics adapter tracks only privacy-safe fields with a default no-op sink.
- Structured merge logs include operational metadata only; raw note payload remains excluded.

## Faz 54 additions

- Scheduled export machine identity uses short-lived JWT with strict scope/audience/issuer checks.
- Machine token is intentionally restricted to export endpoint to avoid privilege bleed.
- Private key material remains CronJob-secret-only; frontend never receives machine credential.

## Faz 55 additions

- S3-compatible upload credentials remain CronJob-only secrets.
- Archive writer should not have delete permission on bucket prefix.
- Object lock governance/compliance should be tested in staging before production retention hardening.

## Faz 56 additions

- Notification realtime transport is SSE over authenticated GET (`/notifications/stream`), not
  WebSocket.
- Event payloads stay minimal and avoid arbitrary metadata leakage.
- Bearer token is not passed through query string for SSE; bearer-mode clients remain polling-only.
- Multi-pod in-memory emitter limitation is accepted with polling fallback until distributed fanout is
  implemented.

## Faz 57 additions

- Redis pub/sub payload includes only routing/event fields; no token/session/cookie data.
- `recipientUserId` is used only for in-memory emitter routing.
- Redis should remain internal-network only with auth and optional TLS.
- Self-echo skip avoids duplicate local delivery when local-first publish is enabled.

## Faz 64 additions

- Durable fanout outbox stores the same minimal SSE payload classes as live events; no secrets or
  full notification bodies.
- At-least-once-ish delivery may duplicate events; clients must treat handlers as idempotent
  (`docs/notification-durable-fanout.md`).

## Faz 58 additions

- Security-critical notifications bypass digest and quiet hours to avoid delayed account protection.
- Digest items should contain minimal content and avoid sensitive metadata leakage.

## Faz 62 additions

- SIEM streaming payloadlari minimal alanlarla sinirlidir; token/secret/cookie verisi tasinmaz.
- SIEM auth sirlari sadece backend secret kaynaklarinda tutulur, frontend'e verilmez.
- Outbox retry/dead-letter modeli, transient SIEM kesintilerinde veri kaybini azaltir.
- Delivery preference APIs are user-scoped through `X-User-Id`; cross-user updates are not allowed.
- Faz 65 workspace notification preference APIs additionally require workspace-service **membership**
  verification via service JWT; non-members receive `403` without reading or mutating overrides.

## Faz 59 additions

- Offline note cache stores recently opened note content in browser IndexedDB; this is a local-device
  privacy risk on shared machines.
- Logout/session clear now removes offline cache to reduce residual data exposure.
- Offline cache is limited to user-opened note documents; admin/audit/export data is excluded.
- Sensitive deployments can disable offline cache entirely via `FRONTEND_OFFLINE_NOTES_ENABLED=false`.

## Faz 67 additions (offline drafts)

- When `FRONTEND_OFFLINE_EDIT_ENABLED` is on (non-default), **unsynced** note snapshots may exist in
  IndexedDB (`offline_note_drafts`), increasing residual-data risk vs read-only cache alone.
- Draft content must not be sent to analytics or remote logs; product copy should warn on shared devices.
- Disable offline editing in strict enterprises via the flag; logout / DB delete still clears drafts
  together with cached notes.
- **Session failure policy (recommended)**: align with logout — wiping the offline DB on `clearSession`
  avoids orphaned sensitive drafts; tradeoff is losing unsynced work (documented in
  [`offline-edit-sync-design.md`](offline-edit-sync-design.md)).

## Faz 68 additions (offline edit MVP)

- Manual sync UI can surface pending drafts in Settings, which may reveal unsynced note intent on
  shared devices; operational guidance remains "logout + clear offline data."
- Draft sync uses existing authenticated API client path (cookie/CSRF or bearer), no separate
  unauthenticated transport.

## Faz 69 additions (offline data encryption)

- Offline draft payloads can be encrypted with session-bound AES-GCM key (memory-only).
- Optional offline cache encryption is available for stricter deployments.
- At-rest exposure is reduced for IndexedDB content, but active-session XSS still remains a risk.
- Encryption-required mode can force read-only fallback when crypto/key is unavailable.

## Faz 70 additions (rollout hardening)

- Production rollout adds strict sync guardrails (attempt cap, stale recovery, conflict-loop stop).
- Local diagnostics intentionally exclude content and are limited to counters/state.

## Faz 74 additions (foreground background sync)

- Background sync foundation is foreground app-level only; no service-worker production sync yet.
- Trigger guardrails require online + authenticated + encryption-ready context before run.
- Auto-safe eligibility excludes conflict/failed/locked/review-required drafts to reduce retry storms and unsafe auto actions.
- Local diagnostics remain aggregate-only; no raw note content, title, or user-level telemetry.

## Faz 75 additions (foreground background sync MVP)

- Prompt mode enforces explicit user consent before sync batch execution.
- Active-note skip guardrail prevents background sync from touching currently edited note drafts.
- Session-expired behavior stops batch immediately; no blind retries across auth failures.
- Foreground-only runtime means sync is inactive when app is closed (no hidden worker behavior).
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

## Enterprise SSO Identity Risks (Faz 60)

- Risk: IdP claim confusion (issuer/audience/group mismatch) could grant excess access.
- Current mitigation: issuer + audience checks, state/nonce replay protection, verified email and
  allowed domain checks.
- SCIM (Faz 61/76): static bearer for provisioning; group nesting validation limits membership cycles
  and depth; bulk is off by default and is non-transactional—integrators must not assume atomic batch
  semantics.
- Remaining gap: no manual secure account linking workflow for all enterprise edge cases.
- Recommended next action: explicit admin account-link approvals where HRIS/IdP ownership is ambiguous.

## Frontend CSP Hardening (Faz 44)

- Risk: Script injection can still perform authenticated actions in cookie mode even when tokens are
  not readable from JS.
- Current mitigation: Runtime-configurable CSP (`report-only`/`enforce`) with strict `script-src 'self'`,
  `object-src 'none'`, `frame-ancestors 'none'`, `base-uri 'self'`, `form-action 'self'`; no
  `dangerouslySetInnerHTML` usage in SPA code paths.
- Remaining gap: `style-src 'unsafe-inline'` is still required for current UI/editor stack and CSP
  violation reports are not persisted by platform backend yet.
- Recommended next action: add CSP report collector + tighten style policy in a controlled phase.

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
- Current mitigation: Gateway CORS origins come from `CORS_ALLOWED_ORIGINS`; credentials can be enabled for cookie auth with explicit origin allow-list.
- Remaining gap: Environment mistakes can broaden origins.
- Recommended next action: Validate production CORS env during deployment.

## CSRF

- Risk: Browser sends authenticated cookie requests without explicit user intent.
- Current mitigation: Faz 41 adds gateway double-submit CSRF validation for unsafe methods using
  csrf cookie + `X-CSRF-Token` header.
- Remaining gap: bypassed bootstrap paths (`/auth/login`, `/auth/signup`) still rely on rate limit
  and credential checks.
- Recommended next action: monitor 403 CSRF error rates and tune client error UX.

## Admin / Audit UI Exposure (Faz 43)

- Risk: Showing operational audit context to unintended browser sessions before hardened RBAC.
- Current mitigation: Route + navigation gates behind `ADMIN_UI_ENABLED`; gateway `GET /admin/audit-events`
  requires authenticated admin allowlist/role checks and signs downstream service JWTs server-side.
- Remaining gap: PLATFORM_ADMIN claim lifecycle + IdP group sync are still pending.
- Recommended next action: Move from allowlist fallback to identity-issued PLATFORM_ADMIN claims and ship
  audit access logs to SIEM (see [`docs/admin-audit-proxy.md`](admin-audit-proxy.md)).

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

## Note conflict merge (Faz 66)

- Diff and suggested merge run **only in the browser** on data the user already received via
  authorized `GET /notes/{id}`; no raw note content is sent to analytics or third parties by this
  feature.
- Merged payloads are still persisted only through normal authenticated `PATCH` + `If-Match`; the
  server does not trust client merge logic beyond standard validation.

## In-App Notification Threat Notes (Faz 45)

- Ownership: user endpoints derive `recipientUserId` from gateway context (`X-User-Id`), not from client payload.
- `actionUrl` is restricted to internal relative `/app/*` paths; protocol URLs and `javascript:` are rejected.
- Notification metadata is sanitized server-side and not rendered directly in UI.
- Message/title are rendered as plain React text nodes (no HTML injection path).

## Audit Export Threat Notes (Faz 48)

- Export endpoint is admin-authorized at gateway; non-admin users receive access denied.
- Internal service JWT remains gateway-only and is never exposed to browser.
- Export metadata is redacted again at gateway export layer (defense in depth).
- CSV output applies formula injection mitigation by prefixing risky leading characters.

## MFA / WebAuthn Threat Notes (Faz 50 design)

- Passkeys provide phishing resistance compared to password-only login.
- Recovery codes must be treated as high-sensitivity secrets; only hashed values are persisted.
- Challenge replay risks are mitigated in design via short TTL challenge storage (Redis).
- Admin/audit surface should enforce MFA in future rollout (`MFA_REQUIRED_FOR_PLATFORM_ADMIN`).

## MFA / WebAuthn Threat Notes (Faz 51 implementation)

- MFA step-up session and challenge artifacts are short-lived Redis keys and are consumed on verify.
- Recovery codes are hash-only in DB and one-time-use (`usedAt`).
- Allowed WebAuthn origins are explicitly configured via `MFA_WEBAUTHN_ALLOWED_ORIGINS`.

## Enterprise admin console (Faz 63)

Browser calls `GET /admin/enterprise/status` and (when enabled) change-request routes under `/admin/enterprise/change-requests` (including **approve/reject** in Faz 78); gateway holds service JWTs for internal status and change-request endpoints. Change requests do not mutate runtime secrets or return secret material; approval only transitions workflow state (`docs/enterprise-admin-write-operations.md`, `docs/admin-change-request-approval-workflow.md`).
No SCIM/SIEM/OIDC secrets are returned — see `docs/enterprise-admin-console.md`.

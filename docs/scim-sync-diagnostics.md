# SCIM Sync Diagnostics (Faz 97)

Faz 97 read-only admin diagnostics ekler. Bu endpointler provider-specific production delta sync veya destructive automation baslatmaz.

## Identity-service internal API

Requires internal service authorization with scope `internal:admin:scim:diagnostics:read`.

- `GET /internal/admin/scim/compatibility/status`
- `GET /internal/admin/scim/sync-runs`
- `GET /internal/admin/scim/sync-checkpoints`
- `GET /internal/admin/scim/delta/readiness` (Faz 115)
- `POST /internal/admin/scim/delta/dry-run` (Faz 115 POC — diagnostic run only)

Compatibility response includes:

- provider type
- delta enabled/mode
- capability flags
- max page size
- last sync status
- warning codes
- checkpoint summary
- last run summary

No response includes SCIM bearer tokens, checkpoint token values, raw request payloads, emails, or user IDs.

## Gateway admin API

Requires `admin:identity:read` or `admin:scim:diagnostics:read`.

- `GET /admin/identity/scim/compatibility/status`
- `GET /admin/identity/scim/sync-runs`
- `GET /admin/identity/scim/sync-checkpoints`
- `GET /admin/identity/scim/delta/readiness`
- `POST /admin/identity/scim/delta/dry-run` (requires `SCIM_DELTA_PROVIDER_POC_ENABLED=true` on identity-service)

Gateway maps service errors to bounded admin errors and does not expose internal service JWT details.

## Delta readiness (Faz 115)

`DeltaReadinessResponse` fields (safe only):

- `providerType`, `deltaPocEnabled`, `dryRunOnly`, `selectedStrategy`, `deltaSource`
- `supportsFiltering`, `supportsPagination`, `supportsPatch`, `supportsRetryAfter`, `capabilityAligned`
- `deprovisionSemantics` (one-line policy text, no PII)
- `lastCheckpoint` — resource type, `checkpointPresent`, status, timestamps (no token value)
- `lastDryRunStatus`, `warnings`

Warning codes include:

- `SCIM_DELTA_PROVIDER_UNSUPPORTED`
- `SCIM_DELTA_DRY_RUN_ONLY`
- `SCIM_DELTA_FILTERING_UNAVAILABLE`
- `SCIM_DELTA_RETRY_AFTER_OBSERVED`
- `SCIM_DELTA_CHECKPOINT_STALE`
- `SCIM_DELTA_MISSING_USER_IGNORED`
- `SCIM_DELTA_RATE_LIMITED`
- `SCIM_DELTA_RAW_PAYLOAD_SUPPRESSED`
- `SCIM_DELTA_REMOTE_FETCH_DISABLED` / `SCIM_DELTA_REMOTE_FETCH_NOT_CONFIGURED` (Faz 117)
- `SCIM_DELTA_REMOTE_FETCH_ATTEMPTED` / `SCIM_DELTA_PROVIDER_RESPONSE_SANITIZED` (Faz 117)

## Delta remote fetch (Faz 117–118)

When `SCIM_DELTA_REMOTE_FETCH_ENABLED=true` and base URL + bearer token are configured, **manual dry-run only** may issue a single bounded **GET** to the provider. Response bodies are discarded after aggregate extraction (`fetchedResourceCount`, `nextCursorPresent`, `pageObserved`). No POST/PATCH/PUT/DELETE; no user/group DB mutation; no raw payload/token/Authorization in API, audit, or logs.

Additional readiness/dry-run fields: `remoteFetchConfigured`, `remoteFetchAttempted`, `fetchedResourceCount`, `pagesObserved`, `nextCursorPresent`, `remoteMultiPageEnabled`, `stoppedReason`, `pageLimitReached`, `resourceLimitReached`.

### Multi-page loop (Faz 119)

- Default: `SCIM_DELTA_REMOTE_MULTI_PAGE_ENABLED=false`, `SCIM_DELTA_REMOTE_MAX_PAGES=1` (single GET, same as Faz 117).
- When enabled: bounded loop on manual dry-run only — max pages, max aggregate resources, page delay, GET-only.
- Raw cursor / next URL never returned in API, audit, or logs; continuation is sanitized internally.
- On 429/5xx/timeout/bad JSON the loop stops (no automatic retry worker).

## Sandbox certification (Faz 120)

Before production scheduler proposals:

1. Collect `scim-delta-remote-fetch-evidence.json` via `scripts/scim/scim-delta-remote-fetch-smoke.sh`
2. Validate with `scripts/scim/validate-scim-delta-evidence.sh`
3. Complete provider checklist — [`scim-delta-provider-certification.md`](scim-delta-provider-certification.md)
4. See [`scim-delta-sandbox-evidence.md`](scim-delta-sandbox-evidence.md)

### Secret wiring (Faz 118)

- Bearer token **never** in Git, ConfigMap, or API responses.
- Helm `scimDeltaRemoteFetch.bearerTokenFromSecret.enabled=true` injects `SCIM_DELTA_REMOTE_BEARER_TOKEN` via `secretKeyRef` on identity-service.
- ConfigMap exposes secret **name/key refs only** (`SCIM_DELTA_REMOTE_TOKEN_SECRET_NAME`, `SCIM_DELTA_REMOTE_TOKEN_SECRET_KEY`) for `remoteFetchConfigured` diagnostics.
- Examples: `deploy/helm/notebook-platform/examples/scim-delta-remote-fetch/`
- Sandbox evidence: `scripts/scim/scim-delta-evidence-formats.md`, `scripts/scim/scim-delta-remote-fetch-smoke.sh`

Dry-run POC creates a `scim_sync_runs` row with `deprovisionedCount=0` and optional checkpoint touch. It does **not** call the IdP or mutate SCIM users/groups.

## Rate-limit / Retry-After (Faz 116)

Readiness and dry-run responses include (sanitized):

- `remoteFetchEnabled`, `rateLimitAware`, `retryAfterObserved`, `retryAfterSeconds`, `retryAfterCapped`
- `nextRecommendedAttemptAt`, `providerErrorClass`, `backoffBaseSeconds`, `httpTimeoutMs`

Config:

- `SCIM_DELTA_REMOTE_FETCH_ENABLED=false` (default)
- `SCIM_DELTA_HTTP_TIMEOUT_MS=3000`
- `SCIM_DELTA_MAX_RETRY_AFTER_SECONDS=300`
- `SCIM_DELTA_BACKOFF_BASE_SECONDS=30`

Dry-run simulation fields (no raw header in response): `simulatedHttpStatus`, `simulatedRetryAfter`, `simulatedTimeout`, `simulatedBadResponse`.

Additional warnings: `SCIM_DELTA_REMOTE_FETCH_DISABLED`, `SCIM_DELTA_RETRY_AFTER_CAPPED`, `SCIM_DELTA_PROVIDER_TIMEOUT`, `SCIM_DELTA_PROVIDER_UNAVAILABLE`, `SCIM_DELTA_PROVIDER_AUTH_FAILED`, `SCIM_DELTA_PROVIDER_BAD_RESPONSE`, `SCIM_DELTA_BACKOFF_RECOMMENDED`.

## Frontend

The enterprise security diagnostics page can show:

- SCIM Provider Compatibility card.
- Provider capability flags.
- Warning codes.
- Checkpoint cards for USER/GROUP.
- Sync Runs table.
- Permission denied / unavailable state.

Feature flag:

- `FRONTEND_SCIM_COMPATIBILITY_DIAGNOSTICS_ENABLED=false` by default.

The UI is read-only. There is no IdP mutation, group edit, or role mutation surface.

## Auditing

Events:

- `SCIM_COMPATIBILITY_STATUS_VIEWED`
- `SCIM_SYNC_CHECKPOINT_CREATED`
- `SCIM_SYNC_CHECKPOINT_UPDATED`
- `SCIM_SYNC_RUN_STARTED`
- `SCIM_SYNC_RUN_COMPLETED`
- `SCIM_SYNC_RUN_FAILED`
- `SCIM_USER_REACTIVATED`
- `SCIM_EXTERNAL_ID_CONFLICT_DETECTED`

Metadata is count/config oriented only. Do not store raw SCIM payloads or SCIM tokens.

## Operational interpretation

- `SCIM_TOKEN_MISSING`: provisioning is enabled but the bearer token is not configured.
- `DELTA_ENABLED_WITH_DISABLED_MODE`: config is inconsistent; keep sync disabled until corrected.
- `DELTA_LAST_MODIFIED_FILTER_UNAVAILABLE`: future delta must use cursor or full-sync fallback.
- `RATE_LIMIT_AWARENESS_DISABLED`: do not enable provider fetch loops.
- `PROVIDER_NESTED_GROUPS_BUT_LOCAL_DISABLED`: provider expects nested groups but local processing is disabled.

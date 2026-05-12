# SCIM Sync Diagnostics (Faz 97)

Faz 97 read-only admin diagnostics ekler. Bu endpointler provider-specific production delta sync veya destructive automation baslatmaz.

## Identity-service internal API

Requires internal service authorization with scope `internal:admin:scim:diagnostics:read`.

- `GET /internal/admin/scim/compatibility/status`
- `GET /internal/admin/scim/sync-runs`
- `GET /internal/admin/scim/sync-checkpoints`

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

Gateway maps service errors to bounded admin errors and does not expose internal service JWT details.

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

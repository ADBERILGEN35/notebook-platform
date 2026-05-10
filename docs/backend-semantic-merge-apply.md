# Backend Semantic Merge Apply (Faz 72)

**Faz 77:** operators may record a GitOps-oriented **change request** for `NOTE_MERGE_ANALYSIS_ENABLED` / `NOTE_MERGE_APPLY_ENABLED` via the enterprise admin console (`docs/enterprise-admin-write-operations.md`); this does not toggle runtime flags automatically.

## Goal

Provide a deterministic, user-triggered, ETag-guarded server-side merge apply contract on top of Faz 71 analyze-only endpoint.

## Endpoint

`POST /notes/{noteId}/merge/apply`

Request:
- `base` snapshot (`etag`, `title`, `contentBlocks`)
- `local` snapshot (`title`, `contentBlocks`)
- `expectedRemoteEtag`
- `mergeVersion`
- optional `idempotencyKey`

Success response:
- `merged=true`
- merged note payload (`title`, `contentBlocks`)
- new `etag`
- `version`
- merge summary

Conflict response (`409`):
- `errorCode=NOTE_MERGE_CONFLICTS`
- `canAutoMerge=false`
- typed conflicts + summary

## Safety Guarantees

- Apply only runs for explicit user action (no silent merge).
- Server re-runs merge analysis before apply.
- `expectedRemoteEtag` must match latest remote ETag, otherwise `412 NOTE_MERGE_REMOTE_CHANGED`.
- Unsupported merge version returns `400 UNSUPPORTED_MERGE_VERSION`.
- Endpoint requires note edit permission and workspace context checks.

## Idempotency

Storage: `note_merge_idempotency_keys`

Columns:
- `workspace_id`, `user_id`, `note_id`, `idempotency_key`
- `request_hash`
- `status` (`IN_PROGRESS`, `COMPLETED`, `FAILED`)
- `result_etag`, `result_version`, `result_note_id`

Important:
- Raw note content is **not** stored in idempotency table.
- Same key + same hash + completed => replay-safe response
- Same key + different hash => `409 IDEMPOTENCY_KEY_REUSED`
- Same key + in-progress => `409 MERGE_APPLY_IN_PROGRESS`

## Reused Update Path

Successful apply reuses existing note update persistence path:
- note row update
- immutable `note_versions` append
- link parsing/rewrite
- search indexing upsert
- existing permission and tenant context rules

## Audit

Success event: `NOTE_MERGE_APPLIED`

Metadata includes:
- `notebookId`
- `mergeVersion`
- `baseEtag`
- `expectedRemoteEtag`
- `idempotencyKeyPresent`

No raw content is written to audit metadata.

## Feature Flags

Backend:
- `NOTE_MERGE_ANALYSIS_ENABLED`
- `NOTE_MERGE_APPLY_ENABLED` (default `false`)
- `NOTE_MERGE_SUPPORTED_VERSIONS` (default `1`)
- `NOTE_MERGE_IDEMPOTENCY_ENABLED` (default `true`)
- `NOTE_MERGE_IDEMPOTENCY_TTL_HOURS` (default `24`)
- `NOTE_MERGE_METRICS_ENABLED` (default `true`)
- `NOTE_MERGE_AUDIT_FAILURES_ENABLED` (default `false`)

Frontend:
- `FRONTEND_BACKEND_MERGE_ANALYSIS_ENABLED`
- `FRONTEND_BACKEND_MERGE_APPLY_ENABLED` (default `false`)

## Rollout and Rollback

Rollout:
1. deploy backend with apply disabled
2. enable backend apply in dev
3. enable frontend apply in dev
4. validate conflict scenarios
5. promote to staging
6. keep prod disabled until confidence

Rollback:
- set `FRONTEND_BACKEND_MERGE_APPLY_ENABLED=false`
- set `NOTE_MERGE_APPLY_ENABLED=false`
- frontend continues existing client-side PATCH fallback flow

## Faz 73 observability update

- Apply and analyze flow now emit Micrometer counters and duration timers.
- Conflict distributions are tracked by type with low-cardinality labels.
- Structured logs include merge result metadata only (no raw title/contentBlocks).

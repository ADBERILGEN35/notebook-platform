# Merge Observability / Conflict Analytics (Faz 73)

## Scope

Adds privacy-safe observability for merge analyze/apply flows without collecting note content or user-level behavior profiles.

Included:
- backend Micrometer counters/timers for merge analyze/apply
- conflict type distribution counters
- apply idempotency outcome counters
- structured merge logs without raw content
- enterprise admin read-only merge status card
- frontend no-op merge analytics adapter contract

Excluded:
- external analytics vendors
- raw content telemetry
- user-level analytics dashboards

## Backend Metrics

Counters:
- `note_merge_analyze_requests_total{result,mergeVersion}`
- `note_merge_analyze_safe_suggestions_total{mergeVersion}`
- `note_merge_conflicts_total{conflictType,source}`
- `note_merge_apply_requests_total{result,mergeVersion}`
- `note_merge_apply_idempotency_total{result}`

Timers:
- `note_merge_analyze_duration_seconds{result}`
- `note_merge_apply_duration_seconds{result}`

Cardinality rules:
- no `userId`, `noteId`, `workspaceId` labels
- only low-cardinality tags (`result`, `mergeVersion`, `conflictType`, `source`)

## Structured Logs

Analyze log fields:
- `result`, `mergeVersion`, `canAutoMerge`, `conflictCount`, `conflictTypes`, `durationMs`

Apply log fields:
- `result`, `mergeVersion`, `expectedRemoteEtagPresent`, `idempotencyKeyPresent`, `conflictCount`, `durationMs`

Never log raw `title` or `contentBlocks`.

## Audit Enrichment

`NOTE_MERGE_APPLIED` metadata includes:
- `mergeVersion`
- `baseEtag`
- `expectedRemoteEtag`
- `resultVersion`
- `idempotencyKeyPresent`
- `conflictCount=0`
- `source=backend_apply`

Optional failure audits (flagged):
- `NOTE_MERGE_APPLY_CONFLICTED`
- `NOTE_MERGE_REMOTE_CHANGED`

Controlled by `NOTE_MERGE_AUDIT_FAILURES_ENABLED` (default `false`).

## Enterprise Console

Gateway enterprise status now includes content merge slice from:
- `GET /internal/admin/status/content` (content-service)

Merge card fields:
- analysis/apply enabled
- supported versions
- idempotency enabled
- metrics enabled
- merge failure audit enabled

Partial status handling includes `contentUnavailable` with warning fallback.

## Frontend Instrumentation

`trackMergeEvent()` no-op adapter contract is introduced for safe future analytics integration.

Tracked action vocabulary:
- `dialog_opened`
- `apply_merge`
- `reload_latest`
- `save_copy`
- `overwrite_latest`
- `cancel`
- `backend_analyze_fallback`

Payload is privacy-safe:
- `source`
- `backendAnalyzeUsed`
- `backendApplyUsed`
- `hasSafeSuggestion`
- `conflictCount`
- `action`

No note content, titles, block text, user IDs, or note IDs.

## Suggested Dashboards and Alerts

Dashboards:
- analyze request rate + result mix
- apply request rate + result mix
- conflict type distribution
- remote-changed ratio
- idempotency replay/reused/in-progress
- p95 analyze/apply duration

Alerts:
- apply `result=error` sustained increase
- remote-changed spike
- unknown/high-risk conflict type spike
- idempotency reused spike
- p95 latency degradation

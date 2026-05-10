# Backend Semantic Merge Design (Faz 71)

## Scope

This phase adds a production-safe foundation for server-side merge analysis only.

Included:
- `POST /notes/{noteId}/merge/analyze` contract
- merge DTOs and validation
- conservative `mergeVersion=1` rules
- frontend feature-flagged integration with client fallback

Excluded:
- silent server merge save
- automatic merge apply without explicit user action
- CRDT/OT, realtime collaboration, rich-text inline merge
- background offline sync and other non-merge roadmap tracks

## Analyze Endpoint

`POST /notes/{noteId}/merge/analyze`

Auth and context:
- requires `X-User-Id`
- requires matching `X-Workspace-Id` context
- requires note edit permission (`canEdit=true`)

Behavior:
- loads latest note as remote snapshot
- validates `base`, `local`, and latest remote `contentBlocks`
- performs three-way conservative analysis (`base`, `local`, `remote`)
- returns suggested merge only when conflict-free
- does not write note/version/audit content payloads

## Request / Response

Request fields:
- `base`: `{ etag?, title, contentBlocks }`
- `local`: `{ title, contentBlocks }`
- `clientMergeVersion`

Response fields:
- `noteId`, `baseEtag`, `remoteEtag`
- `mergeVersion`, `supportedMergeVersions`
- `canAutoMerge`, `hasConflicts`
- `summary`: local/server/conflict summaries
- `suggested`: merged title/content when safe, otherwise `null`
- `conflicts`: typed conflict list

## Merge Versioning

- Supported versions are configured via `NOTE_MERGE_SUPPORTED_VERSIONS` (default `1`).
- Client sends `clientMergeVersion`.
- Unsupported version returns `400 UNSUPPORTED_MERGE_VERSION`.

## Conservative Merge Rules (v1)

Safe examples:
- local title changed while remote content changed
- local content changed while remote title changed
- disjoint block edits where both sides do not touch the same block

Unsafe / conflict:
- same block edited both sides -> `SAME_BLOCK_CHANGED`
- delete vs edit on same block -> `DELETE_VS_EDIT`
- title diverged on both sides -> `TITLE_DIVERGENT`
- missing/duplicate block ids -> `MISSING_BLOCK_ID` / `DUPLICATE_BLOCK_ID`
- move/reorder/structural drift -> `MOVE_OR_STRUCTURE`

If uncertain, analysis marks conflict and does not suggest merge.

## Validation and Error Model

Request/validation errors:
- `INVALID_NOTE_MERGE_REQUEST`
- `INVALID_NOTE_MERGE_BASE`
- `INVALID_NOTE_MERGE_LOCAL`

Runtime merge errors:
- `UNSUPPORTED_MERGE_VERSION`
- `NOTE_MERGE_UNSUPPORTED_CONTENT`
- `NOTE_MERGE_ANALYSIS_DISABLED`

Permission and context errors continue to use existing content-service guards.

## Frontend Integration Strategy

Feature flag:
- `FRONTEND_BACKEND_MERGE_ANALYSIS_ENABLED` (default `false`)

Flow:
1. conflict dialog opens
2. if flag enabled, frontend calls backend analyze endpoint
3. if backend fails/unavailable, fallback to Faz 66 client analysis
4. apply path remains existing client-mediated `PATCH` flow

## Deployment Flags

Content-service:
- `NOTE_MERGE_ANALYSIS_ENABLED` (default `false`)
- `NOTE_MERGE_SUPPORTED_VERSIONS` (default `1`)

Frontend:
- `FRONTEND_BACKEND_MERGE_ANALYSIS_ENABLED` (default `false`)

Recommended rollout:
- enable in dev first
- validate behavior and telemetry
- keep production disabled until confidence is high

## Faz 72 update

Faz 72 adds `POST /notes/{noteId}/merge/apply` (see `backend-semantic-merge-apply.md`) with:
- explicit user-triggered apply only
- expected remote ETag re-check
- idempotency protection
- reuse of note update/version/link/index path

## Faz 73 update

- Added privacy-safe observability for analyze/apply result metrics, duration timers, and conflict type counters.
- Added structured merge logs without raw note content.

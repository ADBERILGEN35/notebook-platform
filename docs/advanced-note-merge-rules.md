# Advanced Note Merge Rules / Block Move Support (Faz 95)

Builds on Faz 66 client three-way merge, Faz 71 backend analyze, Faz 72 backend apply, and Faz 73
observability. **No CRDT/OT. No real-time collaboration. No silent merge.** Same-parent
reorders/moves can now ride along with the existing user-confirmed safe-merge flow; everything
trickier stays a conflict requiring manual resolution.

## State of the merge model

Three snapshots — `base`, `local`, `remote` — and BlockNote `id` per block as the primary identity.
Faz 95 keeps the model but adds **per-block move semantics**.

For every block id we compute:

- `moveStatus` ∈ { `NONE`, `REORDERED` (same parent, index changed), `PARENT_CHANGED` (different parent) }
- `edited`: signature of body (`type`, `props`, `content`, sorted child-id set) changed
- `deleted`: present in base but missing in target

A parent block whose only change is **child reordering** is NOT marked as edited — the signature
sorts child ids so cosmetic sibling reshuffles do not falsely conflict.

## New change types (summary lines)

These appear in `summary.localChanges` / `summary.remoteChanges` and on the frontend dialog. They
are **labels, not conflicts** — they ride along the suggested merge when safe.

| Type | Meaning |
|------|---------|
| `BLOCK_MOVED` | Block changed parent (cross-parent) |
| `BLOCK_REORDERED` | Block stayed in same parent, index changed |
| `BLOCK_PARENT_CHANGED` | Synonym of `BLOCK_MOVED`; emitted on cross-parent moves |
| `BLOCK_INDEX_CHANGED` | Synonym of `BLOCK_REORDERED`; same-parent index change |
| `BLOCK_MOVED_AND_EDITED` | Same block was moved on one side and edited on the other (this surfaces as a conflict) |
| `BLOCK_DELETED_AFTER_MOVE` | One side moved a block, the other deleted it (conflict) |

The wire format is unchanged in shape. `summary.localChanges` already contained human-readable
lines in Faz 71; we now emit `"Reordered block <id> from position N to M"` and
`"Moved block <id> to a different parent"`.

## New conflict types

Bounded list. All emitted via `note_merge_conflicts_total{conflictType=...}` (Faz 73).

| Type | Meaning |
|------|---------|
| `BLOCK_MOVE_CONFLICT` | Both sides moved the same block to different positions |
| `BLOCK_MOVED_AND_EDITED` | One side moved, the other edited the same block |
| `BLOCK_DELETED_AFTER_MOVE` | One side moved, the other deleted the same block |
| `BLOCK_CROSS_PARENT_UNSUPPORTED` | Cross-parent move (out of Faz 95 safe scope) |

Pre-existing v1 types remain: `MISSING_BLOCK_ID`, `DUPLICATE_BLOCK_ID`, `TITLE_DIVERGENT`,
`SAME_BLOCK_CHANGED`, `DELETE_VS_EDIT`.

## Safe vs unsafe matrix

Safe means **a suggested merge is offered** (still user-confirmed before apply). Unsafe means a
conflict is emitted; legacy "Reload latest / Save copy / Overwrite latest" actions remain.

| Local | Remote | Faz 95 outcome |
|-------|--------|----------------|
| Reorders block A (same parent) | Edits unrelated block B | **Safe** — local order + remote edit |
| Reorders multiple blocks at root | Adds new block at root | **Safe** — local order preserved, new block appended |
| Same-parent reorder | Same-target reorder (identical destination) | **Safe** — single move |
| Moves block A | Edits block A | Conflict (`BLOCK_MOVED_AND_EDITED`) |
| Moves block A | Deletes block A | Conflict (`BLOCK_DELETED_AFTER_MOVE`) |
| Moves block A to position X | Moves block A to position Y | Conflict (`BLOCK_MOVE_CONFLICT`) |
| Cross-parent move | Anything (or nothing) | Conflict (`BLOCK_CROSS_PARENT_UNSUPPORTED`) |
| Edits block A | Edits block A differently | Conflict (`SAME_BLOCK_CHANGED`) |
| Deletes block A | Edits block A | Conflict (`DELETE_VS_EDIT`) |
| Missing/duplicate id, unknown block type | — | Conflict (no suggestion) |

When in doubt, the engine emits a conflict. The safe set deliberately stays narrow.

## Frontend dialog

`NoteConflictResolutionDialog` now renders three additional sections when applicable:

- **Moved blocks** (`data-testid="conflict-moved-blocks-summary"`) — cross-parent moves only.
- **Reordered blocks** (`data-testid="conflict-reorder-summary"`) — same-parent index changes.
- **Move conflicts** (`data-testid="conflict-move-conflicts-summary"`) — same-block moved differently on both sides.

Guidance text:

- Safe path with moves: "We can safely combine your block order changes with server content changes. Review the suggested merge before applying."
- Move conflict / cross-parent / moved-and-edited: "Block reordering or moves overlap with other changes. Review manually or save your copy."

The **Apply suggested merge** button only appears when the engine produced a suggestion. There is
no silent auto-apply.

## Service Worker sync boundary (Faz 96)

Service Worker Background Sync does not call backend merge analyze/apply in Faz 96. The POC only
counts eligible/skipped drafts in dry-run diagnostics. Any future worker remote-write path must treat
merge analysis as foreground/UI-owned: `409/412` responses become `CONFLICT`, and the user reviews
safe move/reorder suggestions before applying.

## Backend engine (content-service)

`NoteMergeAnalyzeService` keeps `clientMergeVersion=1` (additive contract). The engine now:

1. Indexes each snapshot by id with parent + sibling index.
2. Computes per-block move status, edits, deletions.
3. Emits per-block conflict matrix above (no blanket `MOVE_OR_STRUCTURE` anymore).
4. Builds suggestion that applies same-parent reorders + content edits if no conflicts exist.

`NoteMergeApplyService` is unchanged — it consumes `NoteMergeAnalyzeService` results, so it
inherits the new behavior. `expectedRemoteEtag`, idempotency, audit, and metrics remain identical.

## Wire contract additions

`NoteMergeAnalyzeResponse` body shape is unchanged. The values of `conflicts[].type` and
`summary.*` strings expanded — old clients render them as opaque labels; new clients render
typed sections.

Optional structured fields exist only on the frontend `MergeAnalysis` (movedBlocks /
reorderedBlocks / moveConflicts arrays). The backend still expresses the same content via
`summary` plus typed `conflicts[]`.

## Observability

`note_merge_conflicts_total{conflictType}` gains four new low-cardinality conflict values
(`BLOCK_MOVE_CONFLICT`, `BLOCK_MOVED_AND_EDITED`, `BLOCK_DELETED_AFTER_MOVE`,
`BLOCK_CROSS_PARENT_UNSUPPORTED`). Pure moves/reorders do **not** count as conflicts. Update
existing dashboards to include the new conflict types and watch the `BLOCK_MOVED_AND_EDITED`
ratio after release.

`note_merge_analyze_safe_suggestions_total` now includes safe move/reorder paths — expect a
moderate increase on dashboards.

Logs are unchanged: `result`, `mergeVersion`, `canAutoMerge`, `conflictCount`, `conflictTypes`,
`durationMs`. No raw note content.

## Security / privacy

- No raw block content in logs, metrics, audit. Block ids and types are not PII; messages
  reference ids only.
- Audit `NOTE_MERGE_APPLIED` metadata unchanged (no block-level move list).
- `summary.localChanges` / `remoteChanges` are returned to authenticated requestors of the same
  note. They contain ids/positions, not block text.

## What this faz does **not** do

- No CRDT/OT or real-time collaboration.
- No character-level inline rich-text merge.
- No silent auto-apply — every safe suggestion still needs user confirmation.
- No cross-parent nested move auto-merge (treated as conflict).
- No block type transformation merge (treated as conflict / unknown type).
- No Service Worker Background Sync, no SCIM delta, no retention/legal hold expansion, no
  workspace notification policy analytics, no break-glass provider rotation work.

## Related docs

- [`note-conflict-diff-merge.md`](note-conflict-diff-merge.md)
- [`backend-semantic-merge-design.md`](backend-semantic-merge-design.md)
- [`backend-semantic-merge-apply.md`](backend-semantic-merge-apply.md)
- [`merge-observability.md`](merge-observability.md)

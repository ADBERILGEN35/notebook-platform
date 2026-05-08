# Note conflict diff / merge (Faz 66)

BlockNote-backed notes use **optimistic concurrency** (`If-Match` / ETag). When a save returns `412 NOTE_CONFLICT`, the client keeps local edits and must resolve against a newer server revision.

This phase adds a **three-way model** and **client-side suggested merge** only. There is **no** silent automatic save, **no** backend merge endpoint, and **no** CRDT/OT or realtime collaboration.

## Three snapshots

| Snapshot | Meaning |
|----------|---------|
| **base** | Last **successfully synced** client state (`lastSavedRef` when the conflicting save failed). Acts as the common ancestor for diff/merge. |
| **local** | Current editor state when the conflict is detected (unsaved). |
| **remote** | Latest server note, fetched when the conflict dialog opens (`GET /notes/{id}`). |

## Block identity

- Primary key: **`id`** on each `NoteBlock`.
- Missing/empty ids, duplicate ids, or **unknown block types** (outside an allowlisted set) → **no** suggested merge; user must reload, copy, or overwrite.
- Diffs operate on the block tree (content, props, children structure, order). **Moves/reorders** are treated as **unsafe** for suggested merge in this phase.

## Safe vs unsafe (suggested merge)

**Suggested merge** is built only when:

- No duplicate/missing ids and no unsupported types.
- No **same-block divergent** edits (both sides changed the same block differently).
- No **delete vs edit** on the same block.
- No **title divergence** (both sides changed the title differently).
- No **detected block moves** between base and either side.

Examples of **safe** patterns implemented on the client:

- **Title-only local** change while **server** changed **content** (blocks unchanged locally vs base).
- **Disjoint edits**: different blocks edited on local vs remote.
- **Local-only root-level additions** appended after remote (nested additions are not auto-suggested).
- **Local delete** of a block the server left identical to base.

Anything outside these rules → **no** suggestion; existing actions remain (**Reload latest**, **Save copy**, **Overwrite**).

## User flow

1. Conflict dialog loads summaries: **Local changes**, **Server changes**, **Conflicts** (if any).
2. If a suggestion exists, **Apply suggested merge** applies merged title/blocks to the editor and issues a normal `PATCH` with the **latest** ETag from the remote fetch.
3. If another `412` occurs, the user stays in conflict resolution.

## Code layout

- `frontend/src/features/notes/utils/blocknote-diff.ts` — block-level diff helpers.
- `frontend/src/features/notes/utils/blocknote-merge.ts` — `analyzeNoteConflict`, `buildMergeSuggestion`, rules above.
- `NoteConflictResolutionDialog` — UI summaries and actions.
- `useNoteAutoSave` — `NoteConflictInfo.baseSnapshot`, `saveMergedAfterConflict`.

## Limitations and non-goals (Faz 66)

- No offline edit queue / sync.
- No inline rich-text character-level merge.
- No backend semantic merge or version compaction.
- Suggested merge is **best-effort** and **conservative**; users should review before applying.

## Future work

- Optional **backend merge** endpoint with stricter invariants.
- Richer move/reorder resolution.
- Offline sync layered on top of the same three-way model (see [`offline-edit-sync-design.md`](offline-edit-sync-design.md); Faz 67 defines draft storage and sync mapping only).

## Offline draft conflicts (Faz 67)

When syncing an offline draft, a `412` should fetch **remote**, then call `analyzeNoteConflict` with
**base** = draft `baseSnapshot`, **local** = `localSnapshot`, **remote** = fresh server note. Reuse the
same dialog component family as online conflicts; **no** silent merge. Additional offline actions
(keep draft as copy, discard draft, retry) are specified in the offline design doc.

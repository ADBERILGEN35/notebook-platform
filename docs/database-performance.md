# Database Performance Review

## Identity Service

Existing indexes:

- `users`: unique `lower(email)`, `status`
- `refresh_tokens`: `token_hash`, `user_id`
- `identity_audit_events`: event type, actor, aggregate, created time

Query review:

- Signup/login use `lower(email)` uniqueness and lookup.
- Refresh rotation uses `token_hash`.
- Audit queries are not public yet, but operational queries usually filter by event type, aggregate or created time.

Open tuning:

- Add `refresh_tokens(user_id, revoked_at)` only if session management queries are introduced.

## Workspace Service

Existing and added indexes:

- `workspaces`: unique slug, active personal owner
- `workspace_members`: user id, workspace id, workspace role
- `notebooks`: workspace id and `workspace_id, archived_at`
- `notebook_members`: user id, workspace id
- `tags`: workspace id, unique active `workspace_id + lower(name) + scope`, `workspace_id + scope + archived_at`
- `invitations`: token hash, lower email, workspace id, expires at, pending workspace/email, workspace pending state
- `workspace_audit_events`: event type, actor, workspace, aggregate, created time

Query review:

- Workspace list by user uses `workspace_members.user_id`.
- Membership checks use composite primary key.
- Internal tag exists uses `tags(id, workspace_id, scope, archived_at)` semantics; primary key plus workspace/scope active index keeps lookup selective.
- Pending invitation checks benefit from partial unique pending index and workspace pending index.
- Faz 11 tenant binding uses `SET LOCAL app.current_workspace_id` once per tenant-scoped transaction when `APP_RLS_ENABLED=true`; this is a small constant overhead and does not change query plans.
- Faz 12 strict header mode reduces forced-RLS resolver ambiguity for aggregate notebook/tag/invitation endpoints by requiring the tenant context before the scoped query path is hardened.

## Content Service

Existing and added indexes:

- `notes`: workspace id, notebook id, parent note id, created by, archived at, GIN search vector, `workspace_id + notebook_id + archived_at`
- `note_versions`: unique `note_id + version_number`, `note_id`, `workspace_id + note_id`
- `note_links`: from, to, `workspace_id + from`, `workspace_id + to`
- `comments`: note id, user id, parent comment id, `note_id + deleted_at`
- `note_tags`: tag id, workspace id, `workspace_id + note_id`
- `content_audit_events`: event type, actor, workspace, aggregate, created time

Query review:

- Notebook note listing uses `notes.notebook_id` and active filters.
- Search uses generated `search_vector` GIN index.
- Backlinks use `note_links.to_note_id`.
- Comment list uses `comments.note_id` and `deleted_at`.
- Faz 11 tenant binding uses `SET LOCAL app.current_workspace_id` once per tenant-scoped transaction when `APP_RLS_ENABLED=true`; note/comment aggregate lookups remain the dominant cost for category B endpoints.
- Faz 12 strict header mode makes those aggregate lookups compatible with a tenant-first RLS rollout without adding new indexes.

Write-cost note:

- Additional indexes were limited to common tenant/activity filters and audit access paths.
- No broad JSONB GIN index was added for `content_blocks`; full content search remains via `search_vector`.

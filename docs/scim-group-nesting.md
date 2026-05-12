# SCIM Group Nesting (Faz 76)

## Data model

- `scim_groups`: `id`, optional `external_id` (unique when present), `display_name`, optional `platform_role`, optional `provider`, `active` (default true), timestamps.
- `scim_group_memberships`: `group_id` (container), `member_type` (`USER` | `GROUP`), either `member_user_id` or `member_group_id`, optional `member_external_id`, `created_at`.
- Uniqueness: one USER row per `(group_id, member_user_id)`; one GROUP row per `(group_id, member_group_id)`.
- Self-membership as a nested group is rejected by DB check and validation.

Legacy `user_scim_group_memberships` was migrated into `scim_group_memberships` (Flyway `V12`).

## Semantics

- Groups may contain **users** and **nested groups** (when `SCIM_GROUP_NESTING_ENABLED=true`).
- **Effective membership** for a user = direct USER memberships plus all **active** ancestor groups found by walking “who contains this group?” edges upward. Inactive groups are skipped (no role contribution, no upward traversal from an inactive direct parent).
- **Platform admin**: `SCIM_ADMIN_GROUPS` lists case-insensitive `displayName` / `externalId` tokens. If any **effective** group matches, login access tokens can include `PLATFORM_ADMIN` (with existing MFA/admin gateway rules). Deprovisioned/inactive users do not receive the claim. Inactive groups never contribute.

### Fine-grained admin RBAC (Faz 79)

When `ADMIN_RBAC_ENABLED=true`, **effective** group keys are also matched against `ADMIN_RBAC_GROUP_*` values to assign fine-grained `PLATFORM_*` roles and `platform_permissions` on access tokens (see [`docs/admin-rbac.md`](admin-rbac.md)). This is additive to the legacy `SCIM_ADMIN_GROUPS` → `PLATFORM_ADMIN` behavior.

## Cycle and depth

- **Cycle**: adding `parent -> child` is rejected if `child` is already an ancestor of `parent` in the GROUP graph. Error detail includes `SCIM_GROUP_CYCLE_DETECTED`.
- **Depth**: longest containment chain (edges) after the new link must not exceed `SCIM_GROUP_NESTING_MAX_DEPTH` (default `5`). Error detail includes `SCIM_GROUP_NESTING_DEPTH_EXCEEDED`.

## API

- `GET|POST /scim/v2/Groups`, `GET|PUT|PATCH|DELETE /scim/v2/Groups/{id}`.
- Group payloads support `members[]` with `type` `User` or `Group`, `value` (user/group UUID or group `externalId`), optional `display`, optional `$ref` (response).
- `DELETE /Groups/{id}`: soft-deprecates the group (`active=false`), clears memberships touching that group, returns `204`. Audit: `SCIM_GROUP_DEPROVISIONED`.

## Limitations (this phase)

- Not a graph database; deep/large graphs rely on Postgres and application validation.
- Full SCIM PATCH for groups is limited to documented paths (`displayName`, `externalId`, `members`).
- Cross-tenant/org SCIM is out of scope.

## Provider compatibility note (Faz 97)

Provider capability flag `SCIM_PROVIDER_SUPPORTS_NESTED_GROUPS` is diagnostic only. It does not enable provider-specific nested group import, IdP group mutation, or a scheduled sync engine. If a provider claims nested-group support while local `SCIM_GROUP_NESTING_ENABLED=false`, diagnostics returns `PROVIDER_NESTED_GROUPS_BUT_LOCAL_DISABLED`.

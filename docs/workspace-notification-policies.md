# Workspace admin notification policies (Faz 85)

Workspace **owners** and **admins** can define per-workspace policies on notification **type** and **channel** (`IN_APP`, `EMAIL`). Members cannot manage policies; they still edit their own workspace notification preferences when enabled, but **effective** delivery follows the precedence below.

## Policy modes

- `USER_CONTROLLED` — No workspace mandate; effective channel state comes from user workspace override (Faz 65) or global preferences (Faz 49).
- `FORCE_ENABLED` — Channel is effectively on for all members; members cannot turn it off for that type/channel in this workspace.
- `FORCE_DISABLED` — Channel is effectively off for all members; members cannot turn it on. **Not allowed** for mandatory security notification types (e.g. `SECURITY_SESSIONS_REVOKED`).

## Precedence

1. **Mandatory / security-critical** platform notifications — always delivered; policies and user prefs cannot disable them.
2. **Workspace admin policy** — `FORCE_ENABLED` / `FORCE_DISABLED` override user workspace and global preferences for that type/channel.
3. **User workspace preference** — when `USER_CONTROLLED` and per-workspace overrides are enabled.
4. **Global preference**
5. **Defaults**

## Digest and quiet hours

Workspace policies do **not** change digest frequency or quiet hours (they remain **global** per user, Faz 58). `FORCE_ENABLED` on **EMAIL** means the user is eligible for email for that event; digest batching and quiet-hour deferral still apply. Security-critical notifications remain immediate.

## Configuration

**notification-service**

- `WORKSPACE_NOTIFICATION_POLICIES_ENABLED` (default `false`)
- `WORKSPACE_NOTIFICATION_POLICY_REASON_REQUIRED` (default `true` in `application.yml`) — when `true`, `FORCE_ENABLED` / `FORCE_DISABLED` require a non-blank `reason` on PATCH.
- Reuses `WORKSPACE_SERVICE_URL` and notification-service outbound JWT to workspace-service (`internal:workspace:permission:read`).

**frontend**

- `FRONTEND_WORKSPACE_NOTIFICATION_POLICIES_ENABLED` / `VITE_WORKSPACE_NOTIFICATION_POLICIES_ENABLED` (default `false`)

## API

- `GET /notification-policies/workspaces/{workspaceId}` — any member; includes `canManagePolicies` and per-channel `manageable`.
- `PATCH /notification-policies/workspaces/{workspaceId}` — owner/admin only; body `{ "updates": [ { "notificationType", "channel", "policyMode", "reason" } ] }`.
- `POST /notification-policies/workspaces/{workspaceId}/reset` — owner/admin only; deletes all policy rows (equivalent to all `USER_CONTROLLED`).

User preference responses (`GET /notification-preferences/workspaces/{workspaceId}`) include `workspacePolicy` and `lockedByPolicy` on each channel when a force policy applies.

## Analytics

Low-cardinality aggregate kind: `SKIPPED_WORKSPACE_ADMIN_POLICY` when delivery is skipped because of `FORCE_DISABLED` while the user’s base preference would have allowed the channel. No workspace identifiers in analytics rows.

## Explicit non-goals (this phase)

Per-role policies, per-notebook policies, org-level policies, platform-admin override of workspace policies, workspace-scoped digest/quiet hours, and policy approval workflows.

## Risk note on `FORCE_DISABLED`

Disabling non-security channels may hide collaboration signals; use with clear operational reason. Security/mandatory types cannot be force-disabled.

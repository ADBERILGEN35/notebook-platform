# Workspace notification preferences (Faz 65)

Users can override **global** notification channel settings **per workspace** for supported notification types. This applies to both **in-app** and **email** channel decisions at notification creation time.

## What stays global

- **Email digest** frequency and batching
- **Quiet hours** and timezone for email delivery
- **Security / mandatory** notification types (they cannot be disabled via workspace overrides)

Workspace overrides only answer: *for this workspace, for this notification type, should this channel be allowed?* Global digest and quiet-hours logic still run after email is allowed at the preference layer.

**Faz 81:** Global vs workspace preference skips are rolled up as aggregate event kinds (no user/workspace dimensions in MVP); see [`notification-analytics-privacy.md`](notification-analytics-privacy.md).

**Faz 85:** Workspace owners/admins can add a **policy layer** (`FORCE_ENABLED` / `FORCE_DISABLED`) that applies to all members. See [`workspace-notification-policies.md`](workspace-notification-policies.md). User workspace overrides are still stored, but **effective** delivery follows policy when not `USER_CONTROLLED`.

## Resolution order

1. If the notification type is mandatory security (e.g. `SECURITY_SESSIONS_REVOKED`), channels stay **enabled**; workspace preference rows are rejected for those types; policies cannot `FORCE_DISABLE` them.
2. If a **workspace admin policy** applies (`FORCE_ENABLED` / `FORCE_DISABLED`), that determines the channel (when `WORKSPACE_NOTIFICATION_POLICIES_ENABLED` is on). See Faz 85 doc.
3. If `WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED` is false, or the type does not support workspace overrides, **global** preferences apply (policy may still apply in step 2).
4. If there is a **per-workspace preference row** for `(userId, workspaceId, type, channel)`, its `enabled` value is used.
5. Otherwise **global** preference applies for that channel.

## API (notification-service)

- `GET /notification-preferences/workspaces/{workspaceId}` — effective + inherited state per overridable type
- `PATCH /notification-preferences/workspaces/{workspaceId}` — body `{ "updates": [ { "notificationType", "channel", "inheritGlobal", "enabled" } ] }`  
  - `inheritGlobal: true` removes the override (inherit global)
  - `inheritGlobal: false` requires `enabled`
- `POST /notification-preferences/workspaces/{workspaceId}/reset` — delete all overrides for that user/workspace

**Authorization:** notification-service calls workspace-service with a **service JWT** to verify the user is a member. Failures map to `403` / `503` (see error codes in notification-service).

## Configuration

- `WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED`
- `WORKSPACE_SERVICE_URL`
- `NOTIFICATION_WORKSPACE_CLIENT_*` (outbound JWT to workspace-service)

Workspace-service must trust notification-service JWTs for the internal permission scope (see `internal-service-auth.md` / workspace `trusted-notification-service`).

## Limitations (out of scope for Faz 65)

- Per-notebook preferences
- Per-workspace digest or quiet hours
- Workspace-admin enforced policies
- Notification analytics

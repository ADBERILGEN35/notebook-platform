# Workspace notification preferences (Faz 65)

Users can override **global** notification channel settings **per workspace** for supported notification types. This applies to both **in-app** and **email** channel decisions at notification creation time.

## What stays global

- **Email digest** frequency and batching
- **Quiet hours** and timezone for email delivery
- **Security / mandatory** notification types (they cannot be disabled via workspace overrides)

Workspace overrides only answer: *for this workspace, for this notification type, should this channel be allowed?* Global digest and quiet-hours logic still run after email is allowed at the preference layer.

## Resolution order

1. If the notification type is mandatory security (e.g. `SECURITY_SESSIONS_REVOKED`), channels stay **enabled**; workspace rows are rejected for those types.
2. If `WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED` is false, or the type does not support workspace overrides, **global** preferences apply.
3. If there is a **per-workspace row** for `(userId, workspaceId, type, channel)`, its `enabled` value is used.
4. Otherwise **global** preference applies for that channel.

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

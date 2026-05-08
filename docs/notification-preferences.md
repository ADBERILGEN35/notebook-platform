# Notification Preferences (Faz 49)

## Scope

Faz 49 adds user-level notification preference management for `IN_APP` and `EMAIL` channels without introducing real-time delivery, digest scheduling, quiet hours, or new services.

## Model

- Entity: `UserNotificationPreference`
- Unique key: `(user_id, notification_type, channel)`
- Channels: `IN_APP`, `EMAIL`
- Fields: `enabled`, `mandatory`, timestamps

Default matrix:

- `SECURITY_SESSIONS_REVOKED`: in-app/email enabled + mandatory
- `WORKSPACE_INVITATION_RECEIVED`: in-app/email enabled
- `COMMENT_ADDED`: in-app enabled, email disabled
- `NOTE_VERSION_RESTORED`: in-app enabled, email disabled
- `SYSTEM_NOTICE`: in-app enabled, email disabled

## APIs

- `GET /notification-preferences`: returns grouped preference list per notification type with labels/descriptions and per-channel state.
- `PATCH /notification-preferences`: updates selected rows for current authenticated user (`X-User-Id` context only).

Validation rules:

- Mandatory rows cannot be disabled.
- Invalid payload/type/channel returns 400.
- Missing rows return `NOTIFICATION_PREFERENCE_NOT_FOUND`.

## Enforcement

- Internal in-app creation checks `IN_APP` preference before creating rows.
- Internal email enqueue checks `EMAIL` preference for mapped user-level events.
- Skipped internal requests return `status=SKIPPED` and `skippedReason=USER_PREFERENCE_DISABLED`.

## Preference vs Suppression

- Preference is a user product choice.
- Suppression is a deliverability/security control.
- If suppression is active, suppression still prevents sending even when preference is enabled.

## Frontend

- Settings page exposes notification preference toggles.
- Mandatory toggles are disabled and show a security requirement message.
- Includes dirty state + explicit `Save changes`.
- Notification center and bell dropdown include links to `/app/settings/notifications`.

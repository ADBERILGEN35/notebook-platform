# Audit Events

Audit events are stored in each service database. There is no public audit query API in Faz 8.

## Failure Policy

Audit writes run in a separate transaction and log an error if persistence fails. MVP decision: audit write failure does not block the business operation. This avoids turning observability storage hiccups into user-facing outages. Security event loss should still alert through logs/metrics in a later phase.

Sensitive values must not be written to metadata:

- password
- refresh token
- invitation token
- internal token
- JWT access token

## Identity Events

Table: `identity_audit_events`

- `USER_SIGNED_UP`
- `USER_LOGIN_SUCCEEDED`
- `USER_LOGIN_FAILED`
- `REFRESH_TOKEN_ROTATED`
- `REFRESH_TOKEN_REUSE_REJECTED`

## Workspace Events

Table: `workspace_audit_events`

- `WORKSPACE_CREATED`
- `WORKSPACE_ARCHIVED`
- `WORKSPACE_MEMBER_ROLE_CHANGED`
- `WORKSPACE_MEMBER_REMOVED`
- `NOTEBOOK_CREATED`
- `NOTEBOOK_MEMBER_CHANGED`
- `INVITATION_CREATED`
- `INVITATION_ACCEPTED`
- `INVITATION_REVOKED`

## Content Events

Table: `content_audit_events`

- `NOTE_CREATED`
- `NOTE_UPDATED`
- `NOTE_RESTORED`
- `NOTE_ARCHIVED`
- `COMMENT_CREATED`
- `COMMENT_RESOLVED`
- `NOTE_TAG_ATTACHED`
- `NOTE_TAG_DETACHED`

## Schema

Common fields:

- `id`
- `event_type`
- `actor_user_id`
- `workspace_id`
- `aggregate_type`
- `aggregate_id`
- `request_id`
- `ip_address`
- `user_agent`
- `metadata`
- `created_at`

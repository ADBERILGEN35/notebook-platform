# Break-glass Approval Workflow (Phase 92)

Phase 92 adds governance workflow foundations on top of break-glass access.

## Approval modes

- `disabled`: session issue path behaves as before, audit + event trail.
- `post_use_review`: session is issued, event becomes `PENDING_REVIEW`.
- `required_before_issue`: request enters approval-required path; token issuance is blocked (foundation mode).

## Config

- `BREAK_GLASS_APPROVAL_MODE=disabled|post_use_review|required_before_issue`
- `BREAK_GLASS_EVENT_LOG_ENABLED=true`
- `BREAK_GLASS_REVIEW_REQUIRED_WITHIN_MINUTES=60`
- `BREAK_GLASS_NOTIFY_SECURITY_ADMINS=true`
- `BREAK_GLASS_REVIEW_API_ENABLED=false`

## API surface

- Internal identity API:
  - `GET /internal/admin/break-glass/events`
  - `GET /internal/admin/break-glass/events/{id}`
  - `POST /internal/admin/break-glass/events/{id}/review`
  - `POST /internal/admin/break-glass/events/{id}/revoke-token`
  - `GET /internal/break-glass/tokens/{jti}/revoked`
- Gateway admin API:
  - `GET /admin/break-glass/events`
  - `GET /admin/break-glass/events/{id}`
  - `POST /admin/break-glass/events/{id}/review`
  - `POST /admin/break-glass/events/{id}/revoke-token`

## Security constraints

- No token/assertion/private key is exposed in event responses.
- Review does not extend already-issued short-lived token.
- Phase 93 adds optional revoke-on-reject + explicit manual revoke flow.
- Admin write remains blocked by default.

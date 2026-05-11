# Break-glass Post-use Review (Phase 92)

This document covers the post-use review operating model for break-glass sessions.

## Workflow

1. Break-glass session is issued (short TTL, no refresh token).
2. Access event is persisted with `PENDING_REVIEW`.
3. Security/admin reviewer inspects and submits decision:
   - `APPROVE`
   - `REJECT`
   - `CLOSE`
4. Review action is audited and visible in admin UI.

## Review policy notes

- Review reason is mandatory.
- Review can trigger revocation when `BREAK_GLASS_REVOKE_ON_REJECT=true`.
- Static-token use can be tracked with rotation-required marker.

## Future work (out of scope)

- Global JWT denylist for non-break-glass tokens.
- Multi-approver workflows.
- Automatic rotation automation.

# Break-glass WebAuthn Foundation (Phase 91)

This phase adds a guarded foundation for WebAuthn-backed break-glass access.

## Scope

- New endpoints:
  - `POST /auth/break-glass/webauthn/challenge`
  - `POST /auth/break-glass/webauthn/verify`
- Disabled by default via `BREAK_GLASS_WEBAUTHN_ENABLED=false`.
- No enrollment UI in this phase.
- No secret material is exposed to frontend responses.

## Security posture

- Break-glass remains disabled by default.
- Reason is still required.
- Session is short-lived and does not issue refresh token.
- Session token includes `break_glass=true` and `break_glass_mode=webauthn`.
- Admin write remains blocked at gateway by default.
- Phase 92 governance can place issued sessions into post-use review workflow.

## Operational notes

- This phase provides challenge/verify foundation and audit signals.
- Credential provisioning lifecycle automation is future work.

# Retention governance (notifications)

Faz 83 introduced the notification retention planner/worker; **Faz 84** adds **legal hold** so destructive purge cannot silently violate investigation or compliance needs.

## Principles

1. **Dry-run first** — default configurations keep workers in dry-run or disabled destructive modes.
2. **Target-scoped holds** — holds apply only to notification retention purge kinds (see `docs/notification-legal-hold.md`).
3. **No auto-release** — `expires_at` is informational/warning only until an admin releases the hold.
4. **Partial purge** — `ALL_NOTIFICATION_RETENTION` blocks every target; narrower scopes block only matching targets. An `ALL` purge run still deletes **unblocked** targets.
5. **Transparency** — retention plan rows expose `blockedByLegalHold`, `purgeableCount`, `activeHoldKeys`, and per-target warnings.

## Out of scope (Faz 84)

- Platform-wide legal hold across non-notification domains  
- Per-tenant retention policy  
- Object storage lifecycle  
- Audit event / user_notification purge  
- eDiscovery export  
- Automated release when `expires_at` passes  

## Related docs

- `docs/notification-retention-worker.md` — worker and planner  
- `docs/notification-retention-policy.md` — retention durations  
- `docs/notification-legal-hold.md` — hold API and model  
- `docs/admin-rbac.md` — permissions  

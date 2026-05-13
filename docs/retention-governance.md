# Retention governance

Faz 83 introduced the notification retention planner/worker; **Faz 84** adds **legal hold** so destructive purge cannot silently violate investigation or compliance needs.

**Faz 98** adds platform-wide target inventory, dry-run planning, and a separate platform legal hold foundation. It does not enable content, audit, workspace, identity, or object-storage purge.

## Principles

1. **Dry-run first** — default configurations keep workers in dry-run or disabled destructive modes.
2. **Target-scoped holds** — notification holds apply to notification purge kinds; platform holds apply to dry-run governance scopes.
3. **No auto-release** — `expires_at` is informational/warning only until an admin releases the hold.
4. **Partial purge** — `ALL_NOTIFICATION_RETENTION` blocks every target; narrower scopes block only matching targets. An `ALL` purge run still deletes **unblocked** targets.
5. **Transparency** — retention plan rows expose `blockedByLegalHold`, `purgeableCount`, `activeHoldKeys`, and per-target warnings.

## Out of scope

- Per-tenant retention policy  
- Object storage lifecycle  
- Audit event / user_notification purge  
- eDiscovery export  
- Automated release when `expires_at` passes  
- Platform-wide destructive purge automation

## Related docs

- `docs/notification-retention-worker.md` — worker and planner  
- `docs/notification-retention-policy.md` — retention durations  
- `docs/notification-legal-hold.md` — hold API and model  
- `docs/platform-retention-governance.md` — platform-wide dry-run governance
- `docs/platform-retention-target-inventory.md` — platform data-class inventory
- `docs/platform-legal-hold-model.md` — platform legal hold scopes
- `docs/admin-rbac.md` — permissions  

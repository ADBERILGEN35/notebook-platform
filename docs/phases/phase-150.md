# Faz 150: Frontend Remaining Design Reconciliation + Onboarding Polish

## Scope

Reconcile remaining Stitch designs with implementation. **No backend changes.** **No production flags.**

## Deliverables

- [frontend-design-reconciliation.md](../frontend-design-reconciliation.md)
- `features/onboarding/` — wizard on hub when empty
- Hub, GitOps mobile/a11y, conflict modal, RBAC panel polish
- Vitest: `phase-150-design-reconciliation.test.tsx`

## Route decision

Onboarding uses **conditional wizard on `/app`** (no new `/app/onboarding/*` routes) to avoid router churn.

## Validation

```bash
cd frontend && npm test && npx tsc -b && npm run build
npx playwright test tests/e2e --project=chromium
```

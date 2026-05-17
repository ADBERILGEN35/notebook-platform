# Faz 123: Break-glass Revocation Staging Drill + Evidence Gate

## Hedef

Staging’de break-glass revocation zincirini kanıtlayan sanitized drill script, CI fixtures ve `workflow_dispatch` evidence gate. **Production defaultları değişmez.**

## Scope

| Deliverable | Path |
|-------------|------|
| Drill script | `scripts/security/break-glass-revocation-drill.sh` |
| Orchestrator | `scripts/security/run-break-glass-revocation-evidence.sh` |
| Workflow | `.github/workflows/break-glass-revocation-readiness.yml` |
| Runbook | `docs/break-glass-revocation-staging-drill.md` |

## Non-goals

Live drill on PR, production flag changes, normal token denylist, token logging.

Detay: [`phase-123-summary.md`](phase-123-summary.md).

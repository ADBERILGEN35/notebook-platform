# Faz 121: SCIM Delta Staging Sandbox Evidence Gate + CR Handoff

## Hedef

Staging sandbox dry-run için GitHub Actions gate: sanitized artifacts, Step Summary, certification result mapping, graceful skip without secrets. **No live run on PRs.**

## Scope

| Deliverable | Path |
|-------------|------|
| Workflow | `.github/workflows/scim-delta-readiness.yml` |
| Orchestrator | `scripts/scim/run-scim-delta-sandbox-evidence.sh` |
| Report | `scripts/scim/write-scim-delta-sandbox-report.py` |
| Docs | `scim-delta-sandbox-evidence.md`, certification, production-readiness |

## Jobs

- `scim-delta-fixtures` — PR + main: fixtures + check-no-secrets
- `scim-delta-sandbox-evidence` — staging push + manual dispatch only

## Non-goals

Production scheduler, provider mutation, live sandbox on PR pipeline.

Detay: [`phase-121-summary.md`](phase-121-summary.md).

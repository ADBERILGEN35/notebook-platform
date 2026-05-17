# Faz 133: Staging PP Secrets Setup + Workflow Dispatch Evidence

## Hedef

PP-1/PP-2 workflow’larının canlı staging’de çalışması için GitHub secrets/variables, secret probe, `gh workflow run`, artifact indirme ve bundle input hazırlığını netleştirmek. **Production flag açma yok.**

Önceki faz: [phase-132-summary.md](phase-132-summary.md).

## Scope

| # | Deliverable |
|---|-------------|
| 1 | [backend-staging-pp-secrets-setup.md](../backend-staging-pp-secrets-setup.md) |
| 2 | `check-staging-pp-secrets.sh` (readiness + `--json` + `--check-github`) |
| 3 | `download-backend-pp-artifacts.sh` |
| 4 | `run-backend-live-staging-pp-evidence.sh` (`--probe-only`, `--dispatch-github`, `--downloads-base`) |
| 5 | CI: `ci-check-staging-pp-secrets.sh`, `ci-download-backend-pp-artifacts.sh` |
| 6 | [phase-133-summary.md](phase-133-summary.md) |

Detay: [phase-133-summary.md](phase-133-summary.md).

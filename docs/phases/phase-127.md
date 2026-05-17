# Faz 127: Backend Release Candidate Smoke Suite + Final CI Gate

## Hedef

Backend production-ready kararı öncesi tek giriş noktası: secret scan, dangerous defaults, fixture gates, flag plan guard, targeted Gradle testleri ve Helm render — `ci-backend-rc-readiness.sh` + `backend-rc-readiness` workflow.

## Deliverables

| Artifact | Path |
|----------|------|
| RC gate script | `scripts/security/ci-backend-rc-readiness.sh` |
| Report builder | `scripts/security/build_backend_rc_readiness_report.py` |
| Workflow | `.github/workflows/backend-rc-readiness.yml` |
| Outputs | `backend-rc-readiness-results.json`, `backend-rc-readiness-summary.md` |

## Verdicts

| Verdict | Meaning |
|---------|---------|
| `PASS` | All checks pass |
| `PASS_WITH_ENVIRONMENT_SKIPS` | Pass with helm/gradle skip (documented) |
| `FAIL` | One or more hard failures |

## Non-goals

Production flag açma, runtime feature değişikliği, Docker/Testcontainers zorunlu gate, frontend toolchain.

Full Docker/Testcontainers verification: Faz 128 (`ci-backend-docker-check.sh`).

Detay: [`phase-127-summary.md`](phase-127-summary.md).

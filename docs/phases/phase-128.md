# Faz 128: Backend Docker/Testcontainers CI + Full Gradle Check Gate

## Hedef

Faz 127 RC gate’in dışında kalan **Docker/Testcontainers** ve **full Gradle `check` + `rlsIntegrationTest`** doğrulamasını izole, raporlanabilir bir CI kapısına taşımak.

## Deliverables

| Artifact | Path |
|----------|------|
| Docker CI script | `scripts/security/ci-backend-docker-check.sh` |
| Report builder | `scripts/security/build_backend_docker_ci_report.py` |
| Workflow | `.github/workflows/backend-docker-ci.yml` |
| Outputs | `backend-docker-ci-results.json`, `backend-docker-ci-summary.md` |

## Gate comparison

| Gate | Script | Docker | Gradle scope |
|------|--------|--------|--------------|
| RC (Faz 127) | `ci-backend-rc-readiness.sh` | Optional skip | Targeted test filters |
| Docker CI (Faz 128) | `ci-backend-docker-check.sh` | **Required on CI** | `check` + `rlsIntegrationTest` (default) |

## Non-goals

Production flag açma, runtime feature değişikliği, PR zorunlu ağır gate.

Detay: [`phase-128-summary.md`](phase-128-summary.md).

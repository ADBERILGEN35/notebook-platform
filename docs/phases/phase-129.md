# Faz 129: Backend Docker CI Green Fix + Release Candidate Evidence

## Hedef

Faz 128 Docker CI `FAIL` kök nedeni `:api-gateway:spotlessJavaCheck` format ihlallerini gidermek; gate’leri yeniden koşarak release candidate evidence dokümanlarını güncellemek.

## Deliverables

| Action | Path / command |
|--------|----------------|
| Spotless fix | `api-gateway` (format only) |
| Verify | `./gradlew :api-gateway:spotlessCheck` |
| Docker CI | `scripts/security/ci-backend-docker-check.sh` |
| RC gate | `scripts/security/ci-backend-rc-readiness.sh` |
| Docs | readiness review, checklist, production-readiness |

## Non-goals

Yeni feature, production flag açma, business logic değişikliği, test disable.

Detay: [`phase-129-summary.md`](phase-129-summary.md).
